import asyncio
import json
import os
import time
from contextlib import asynccontextmanager, contextmanager
from datetime import date, datetime, timedelta, timezone
from uuid import uuid4

import psycopg
from botocore.exceptions import BotoCoreError, ClientError
from fastapi import FastAPI, HTTPException
from psycopg.rows import dict_row

from app import queries
from app.common import ch, ch_rows, log, pg, retry, s3


class Busy(Exception):
    pass


@contextmanager
def date_lock(day):
    with pg() as conn:
        conn.autocommit = True
        # Also used by export: a late export cannot overwrite a newer generation.
        locked = conn.execute('SELECT pg_try_advisory_lock(5001,%s)', (day.toordinal(),)).fetchone()[0]
        if not locked:
            raise Busy(f'Date {day} is already being processed')
        try:
            yield
        finally:
            conn.execute('SELECT pg_advisory_unlock(5001,%s)', (day.toordinal(),))


def write_postgres(day, values, computed_at, run_id):
    def operation():
        with pg() as conn:
            conn.execute('DELETE FROM daily_metrics WHERE metric_date=%s', (day,))
            with conn.cursor() as cursor:
                cursor.executemany('''
                    INSERT INTO daily_metrics(metric_date,metric_name,dimension,value,computed_at,run_id)
                    VALUES (%s,%s,%s,%s,%s,%s)
                    ON CONFLICT(metric_date,metric_name,dimension) DO UPDATE
                    SET value=excluded.value,computed_at=excluded.computed_at,run_id=excluded.run_id
                ''', [(day, name, dim, value, computed_at, run_id) for name, dim, value in values])
            if day < datetime.now(timezone.utc).date():
                conn.execute('''INSERT INTO export_jobs(metric_date,status) VALUES(%s,'pending')
                    ON CONFLICT(metric_date) DO UPDATE SET status='pending',last_error=NULL''', (day,))
    retry(operation, (psycopg.OperationalError, psycopg.InterfaceError))


def compute(day):
    if day > datetime.now(timezone.utc).date():
        raise ValueError('Cannot aggregate a future date')
    started = time.monotonic()
    log.info('aggregation started date=%s', day)
    with date_lock(day):
        computed_at = datetime.now(timezone.utc)
        run_id = uuid4()
        params = {'day': day, 'watermark': computed_at.strftime('%Y-%m-%d %H:%M:%S.%f')}
        daily = ch_rows(queries.DAILY, params)[0]
        values = [(name, '', daily[field]) for name, field in (
            ('dau', 'dau'), ('view_started', 'started'), ('view_finished', 'finished'),
            ('average_watch_seconds', 'average_watch_seconds'), ('conversion', 'conversion'))]
        for row in ch_rows(queries.MOVIES, params):
            values.append(('movie_views', row['movie_id'], int(row['views'])))
        for row in ch_rows(queries.DEVICES, params):
            values.append(('device_events', row['device_type'], int(row['events'])))
        cohort = ch_rows(queries.RETENTION, params)
        size = int(cohort[0]['cohort_size']) if cohort else 0
        values.append(('cohort_size', '', size))
        retention = {int(row['day_number']): int(row['returned_users']) / size for row in cohort}
        for age in range(8):
            value = retention.get(age) if day + timedelta(days=age) <= computed_at.date() else None
            values.append(('retention', str(age), value))
        for age in (1, 7):
            value = retention.get(age) if day + timedelta(days=age) <= computed_at.date() else None
            values.append((f'retention_d{age}', '', value))

        stamp = computed_at.strftime('%Y-%m-%d %H:%M:%S.%f')
        rows = [{'metric_date': str(day), 'metric_name': name, 'dimension': dim, 'value': value,
                 'computed_at': stamp, 'run_id': str(run_id)} for name, dim, value in values]
        ch('INSERT INTO cinema.aggregate_values FORMAT JSONEachRow\n' + '\n'.join(map(json.dumps, rows)))
        write_postgres(day, values, computed_at, run_id)
        # Publish only complete snapshots. An interrupted run is invisible to Grafana.
        ch('INSERT INTO cinema.aggregate_runs FORMAT JSONEachRow\n' + json.dumps({
            'metric_date': str(day), 'run_id': str(run_id), 'computed_at': stamp,
            'source_events': int(daily['source_events']),
        }))
    duration = time.monotonic() - started
    log.info('aggregation completed date=%s source_events=%s metrics=%s duration=%.3fs',
             day, daily['source_events'], len(values), duration)
    return {'date': str(day), 'run_id': str(run_id), 'source_events': int(daily['source_events']),
            'metrics': len(values), 'duration_seconds': round(duration, 3)}


def export_day(day):
    with date_lock(day):
        with pg() as conn:
            conn.row_factory = dict_row
            rows = conn.execute('''SELECT metric_date,metric_name,dimension,value,computed_at,run_id
                FROM daily_metrics WHERE metric_date=%s ORDER BY metric_name,dimension''', (day,)).fetchall()
        if not rows:
            raise ValueError('No aggregates for this date; run recompute first')
        key = f'daily/{day}/aggregates.json'
        payload = json.dumps({'date': str(day), 'metrics': rows}, default=str, ensure_ascii=False).encode()
        storage = s3()
        retry(lambda: storage.put_object(Bucket=os.environ['S3_BUCKET'], Key=key, Body=payload,
                                         ContentType='application/json'), (BotoCoreError, ClientError))
        with pg() as conn:
            conn.execute('''INSERT INTO export_jobs(metric_date,status,exported_at) VALUES(%s,'done',now())
                ON CONFLICT(metric_date) DO UPDATE SET status='done',last_error=NULL,exported_at=now()''', (day,))
    log.info('export completed date=%s key=%s metrics=%s', day, key, len(rows))
    return {'date': str(day), 'key': key, 'metrics': len(rows)}


def run_cycle():
    today = datetime.now(timezone.utc).date()
    for offset in reversed(range(int(os.getenv('RECOMPUTE_DAYS', '10')) + 1)):
        day = today - timedelta(days=offset)
        try:
            compute(day)
        except Busy:
            log.info('aggregation skipped busy date=%s', day)
        except Exception:
            log.exception('aggregation failed date=%s', day)
    with pg() as conn:
        pending = conn.execute("SELECT metric_date FROM export_jobs WHERE status='pending' ORDER BY metric_date").fetchall()
    for (day,) in pending:
        try:
            export_day(day)
        except Busy:
            continue
        except Exception as exc:
            log.exception('export failed date=%s; will retry next cycle', day)
            with pg() as conn:
                conn.execute('UPDATE export_jobs SET last_error=%s WHERE metric_date=%s', (str(exc)[:1000], day))


async def scheduler():
    while True:
        try:
            await asyncio.to_thread(run_cycle)
        except Exception:
            log.exception('scheduler cycle failed; will retry next cycle')
        await asyncio.sleep(float(os.getenv('AGGREGATION_INTERVAL_SECONDS', '60')))


@asynccontextmanager
async def lifespan(app):
    task = asyncio.create_task(scheduler())
    yield
    task.cancel()
    await asyncio.gather(task, return_exceptions=True)


app = FastAPI(title='Aggregation Service', lifespan=lifespan)


@app.get('/health')
def health():
    try:
        ch('SELECT 1')
        with pg() as conn:
            conn.execute('SELECT 1')
    except Exception as exc:
        raise HTTPException(503, 'Analytics databases unavailable') from exc
    return {'status': 'ok'}


@app.post('/recompute')
def recompute(date: date):
    try:
        return compute(date)
    except Busy as exc:
        raise HTTPException(409, str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc
    except Exception as exc:
        log.exception('manual recompute failed date=%s', date)
        raise HTTPException(503, 'Recompute failed; retry is safe') from exc


@app.post('/exports')
def export(date: date):
    # Persist request before I/O. Failed manual exports are retried by the scheduler.
    try:
        with pg() as conn:
            conn.execute("INSERT INTO export_jobs(metric_date,status) VALUES(%s,'pending') "
                         "ON CONFLICT(metric_date) DO UPDATE SET status='pending'", (date,))
        return export_day(date)
    except Busy as exc:
        raise HTTPException(409, str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc
    except Exception as exc:
        log.exception('manual export failed date=%s', date)
        raise HTTPException(503, 'Export queued for retry') from exc


@app.get('/metrics')
def metrics(date: date):
    with pg() as conn:
        conn.row_factory = dict_row
        return conn.execute('SELECT metric_date,metric_name,dimension,value,computed_at FROM daily_metrics '
                            'WHERE metric_date=%s ORDER BY metric_name,dimension', (date,)).fetchall()
