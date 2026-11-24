import json
import io
import os
import struct
import time
from datetime import date, datetime, timedelta, timezone
from uuid import uuid4

import httpx
import pytest
from confluent_kafka import Producer
from fastavro import schemaless_writer

from app.common import ROOT, ch_rows, pg, s3

MOVIE = os.getenv('MOVIE_URL', 'http://movie-service:8000')
AGG = os.getenv('AGGREGATION_URL', 'http://aggregation-service:8000')


def wait_for(check, timeout=90):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        result = check()
        if result:
            return result
        time.sleep(0.5)
    raise AssertionError('Timed out waiting for pipeline')


def recompute(day):
    def request():
        response = httpx.post(AGG + '/recompute', params={'date': str(day)}, timeout=120)
        if response.status_code == 409:
            return False
        response.raise_for_status()
        return response.json()
    return wait_for(request)


def test_end_to_end_aggregates_deduplication_and_export():
    prefix = uuid4().hex
    day = date(2001, 1, 1) + timedelta(days=int(prefix[:8], 16) % 6000)
    events = []

    def event(user, offset, kind, progress=0):
        return {
            'event_id': str(uuid4()), 'user_id': f'{prefix}-{user}', 'movie_id': f'test-movie-{user % 2}',
            'event_type': kind,
            'timestamp': datetime.combine(day + timedelta(days=offset), datetime.min.time(), timezone.utc)
                .replace(hour=12, minute=1 if kind == 'VIEW_FINISHED' else 0).isoformat(),
            'device_type': 'TV', 'session_id': f'{prefix}-{user}-{offset}', 'progress_seconds': progress,
        }

    for user in range(4):
        events.append(event(user, 0, 'VIEW_STARTED'))
        if user < 3:
            events.append(event(user, 0, 'VIEW_FINISHED', (user + 1) * 100))
    events += [event(0, 1, 'VIEW_STARTED'), event(1, 7, 'VIEW_STARTED')]
    with httpx.Client(timeout=30) as client:
        for payload in events:
            response = client.post(MOVIE + '/events', json=payload)
            response.raise_for_status()
            assert response.json()['event_id'] == payload['event_id']
        # Simulate a client retry with the same immutable event.
        client.post(MOVIE + '/events', json=events[0]).raise_for_status()

    wait_for(lambda: int(ch_rows('''SELECT count() AS n FROM cinema.raw_events FINAL
        WHERE startsWith(user_id,{prefix:String})''', {'prefix': prefix})[0]['n']) == len(events))
    raw = ch_rows('SELECT * FROM cinema.raw_events FINAL WHERE event_id={id:UUID}', {'id': events[0]['event_id']})
    assert len(raw) == 1
    for name in ('event_id', 'user_id', 'movie_id', 'event_type', 'device_type', 'session_id', 'progress_seconds'):
        assert raw[0][name] == events[0][name]
    assert datetime.fromisoformat(raw[0]['timestamp']).replace(tzinfo=timezone.utc) == datetime.fromisoformat(events[0]['timestamp'])

    recompute(day)
    first = httpx.get(AGG + '/metrics', params={'date': str(day)}).json()
    metrics = {(row['metric_name'], row['dimension']): row['value'] for row in first}
    assert metrics['conversion', ''] == pytest.approx(0.75)
    assert metrics['average_watch_seconds', ''] == pytest.approx(200)
    assert metrics['retention_d1', ''] == pytest.approx(0.25)
    assert metrics['retention_d7', ''] == pytest.approx(0.25)
    assert metrics['retention', '0'] == 1
    assert metrics['retention', '2'] == 0
    assert metrics['dau', ''] >= 4
    recompute(day)
    second = httpx.get(AGG + '/metrics', params={'date': str(day)}).json()
    assert {(r['metric_name'], r['dimension']): r['value'] for r in second} == metrics
    assert len(first) == len(second)
    with pg() as conn:
        total = conn.execute('SELECT count(*) FROM daily_metrics WHERE metric_date=%s', (day,)).fetchone()[0]
    assert total == len(metrics)
    current = ch_rows('SELECT * FROM cinema.metrics_current WHERE metric_date={day:Date}', {'day': day})
    assert len(current) == total

    for _ in range(2):
        httpx.post(AGG + '/exports', params={'date': str(day)}, timeout=45).raise_for_status()
    storage = s3()
    key = f'daily/{day}/aggregates.json'
    result = storage.list_objects_v2(Bucket=os.environ['S3_BUCKET'], Prefix=f'daily/{day}/')
    assert [row['Key'] for row in result['Contents']] == [key]
    exported = json.loads(storage.get_object(Bucket=os.environ['S3_BUCKET'], Key=key)['Body'].read())
    assert exported['date'] == str(day)
    assert len(exported['metrics']) == total
    assert all(row['computed_at'] for row in exported['metrics'])


def test_registry_versions_and_invalid_http():
    registry = os.environ['SCHEMA_REGISTRY_URL']
    response = httpx.get(registry + '/subjects/movie-events-value/versions')
    response.raise_for_status()
    assert len(response.json()) >= 2
    incompatible = json.loads((ROOT / 'schemas/movie-event-v2.avsc').read_text())
    incompatible['fields'][1]['type'] = 'int'
    response = httpx.post(registry + '/compatibility/subjects/movie-events-value/versions/latest',
                          json={'schema': json.dumps(incompatible)})
    response.raise_for_status()
    assert response.json()['is_compatible'] is False
    response = httpx.post(MOVIE + '/events', json={'event_type': 'BOUGHT'})
    assert response.status_code == 422


def test_empty_date_and_immature_retention():
    day = date(2000, 1, 1)
    recompute(day)
    rows = httpx.get(AGG + '/metrics', params={'date': str(day)}).json()
    metrics = {row['metric_name']: row['value'] for row in rows if not row['dimension']}
    assert metrics['dau'] == 0
    assert metrics['conversion'] is None
    assert metrics['average_watch_seconds'] is None
    assert metrics['retention_d1'] is None
    today = datetime.now(timezone.utc).date()
    recompute(today)
    rows = httpx.get(AGG + '/metrics', params={'date': str(today)}).json()
    assert all(row['value'] is None for row in rows
               if row['metric_name'] == 'retention' and int(row['dimension']) > 0)


def test_old_schema_can_still_be_consumed():
    registry = os.environ['SCHEMA_REGISTRY_URL']
    response = httpx.get(registry + '/subjects/movie-events-value/versions/1')
    response.raise_for_status()
    schema = json.loads(response.json()['schema'])
    event_id = str(uuid4())
    record = {'event_id': event_id, 'user_id': event_id, 'movie_id': 'legacy-film',
              'event_type': 'VIEW_STARTED', 'timestamp': datetime(2000, 2, 1, tzinfo=timezone.utc),
              'device_type': 'TV', 'session_id': event_id, 'progress_seconds': 0}
    wire = io.BytesIO()
    wire.write(struct.pack('>bI', 0, response.json()['id']))
    schemaless_writer(wire, schema, record)
    producer = Producer({'bootstrap.servers': os.environ['KAFKA_BOOTSTRAP_SERVERS'], 'acks': 'all'})
    errors = []
    producer.produce('movie-events', key=event_id, value=wire.getvalue(),
                     on_delivery=lambda error, _: errors.append(error) if error else None)
    assert producer.flush(30) == 0
    assert not errors
    rows = wait_for(lambda: ch_rows('SELECT * FROM cinema.raw_events FINAL WHERE event_id={id:UUID}', {'id': event_id}))
    assert rows[0]['search_query'] is None


def test_grafana_provisioning_and_queries():
    url = os.getenv('GRAFANA_URL', 'http://grafana:3000')
    with httpx.Client(base_url=url, auth=('admin', 'adminadmin'), timeout=30) as client:
        response = client.get('/api/dashboards/uid/cinema-analytics')
        response.raise_for_status()
        panels = response.json()['dashboard']['panels']
        assert len(panels) >= 3
        for panel in panels:
            target = panel['targets'][0]
            sql = target['rawSql'].replace('$__fromTime', "toDateTime('2000-01-01 00:00:00')")
            sql = sql.replace('$__toTime', "toDateTime('2030-01-01 00:00:00')")
            assert 'metrics_current' in sql
            assert isinstance(ch_rows(sql), list)
            query = client.post('/api/ds/query', json={
                'from': str(int((time.time() - 14 * 86400) * 1000)),
                'to': str(int(time.time() * 1000)),
                'queries': [target | {
                    'datasource': {'type': 'grafana-clickhouse-datasource', 'uid': 'clickhouse'},
                    'intervalMs': 60000, 'maxDataPoints': 1000,
                }],
            })
            query.raise_for_status()
            result = query.json()['results']['A']
            assert not result.get('error'), result
        response = client.get('/api/datasources/uid/clickhouse/health')
        response.raise_for_status()
        assert response.json()['status'] == 'OK'


def test_s3_can_allocate_a_separate_collection():
    # Existing analytics volumes must not consume every slot available to filer logs/new buckets.
    storage = s3()
    bucket = 'growth-test-' + uuid4().hex
    storage.create_bucket(Bucket=bucket)
    try:
        storage.put_object(Bucket=bucket, Key='probe.json', Body=b'{"ready":true}')
        result = storage.get_object(Bucket=bucket, Key='probe.json')
        assert result['Body'].read() == b'{"ready":true}'
    finally:
        storage.delete_object(Bucket=bucket, Key='probe.json')
        storage.delete_bucket(Bucket=bucket)
