import asyncio
import io
import json
import os
import random
import struct
import threading
from contextlib import asynccontextmanager
from datetime import datetime, timedelta, timezone
from uuid import NAMESPACE_URL, uuid4, uuid5

import httpx
from confluent_kafka import KafkaException, Producer
from fastapi import FastAPI, HTTPException
from fastavro import parse_schema, schemaless_writer
from fastavro.validation import validate

from app.common import ROOT, log
from app.models import GenerateRequest, MovieEvent


class Publisher:
    def __init__(self):
        self.schema = parse_schema(json.loads((ROOT / 'schemas/movie-event-v2.avsc').read_text()))
        response = httpx.post(
            os.environ['SCHEMA_REGISTRY_URL'] + '/subjects/movie-events-value',
            json={'schema': (ROOT / 'schemas/movie-event-v2.avsc').read_text()}, timeout=10,
        )
        response.raise_for_status()
        self.schema_id = response.json()['id']
        self.producer = Producer({
            'bootstrap.servers': os.environ['KAFKA_BOOTSTRAP_SERVERS'],
            'acks': 'all', 'enable.idempotence': True,
            'retries': 5, 'retry.backoff.ms': 100, 'retry.backoff.max.ms': 1600,
            'delivery.timeout.ms': 15000, 'request.timeout.ms': 5000,
            'max.in.flight.requests.per.connection': 5,
        })

    def publish(self, event):
        record = event.avro_record()
        validate(record, self.schema)
        buffer = io.BytesIO()
        buffer.write(struct.pack('>bI', 0, self.schema_id))
        schemaless_writer(buffer, self.schema, record)
        delivered = threading.Event()
        result = {}

        def callback(error, message):
            result.update(error=error, partition=message.partition(), offset=message.offset())
            delivered.set()

        self.producer.produce('movie-events', key=event.user_id, value=buffer.getvalue(), on_delivery=callback)
        while not delivered.is_set():
            self.producer.poll(0.1)
        if result['error']:
            raise KafkaException(result['error'])
        log.info('published event_id=%s type=%s timestamp=%s', event.event_id, event.event_type.value, event.timestamp.isoformat())
        return {'event_id': str(event.event_id), 'partition': result['partition'], 'offset': result['offset']}


def session_events(user, movie, started, device='TV', complete=True, identity=None):
    session = identity or str(uuid4())
    steps = [('VIEW_STARTED', 0, 0), ('VIEW_PAUSED', 300, 300), ('VIEW_RESUMED', 360, 300)]
    if complete:
        steps += [('VIEW_FINISHED', 1260, 1200), ('LIKED', 1261, 0)]
    events = []
    for kind, seconds, progress in steps:
        events.append(MovieEvent(
            event_id=uuid5(NAMESPACE_URL, f'{session}:{kind}'), user_id=user, movie_id=movie,
            event_type=kind, timestamp=started + timedelta(seconds=seconds), device_type=device,
            session_id=session, progress_seconds=progress,
        ))
    return events


def generate_history(publisher, users=8, days=10, prefix=None):
    prefix = prefix or f'manual-{uuid4().hex[:8]}'
    today = datetime.now(timezone.utc).replace(hour=9, minute=0, second=0, microsecond=0)
    count = 0
    for cohort in range(days):
        first = today - timedelta(days=days - cohort)
        for index in range(users):
            user = f'{prefix}-{first.date()}-{index}'
            rng = random.Random(f'{prefix}:{first.date()}:{index}')
            for day in range(min(8, days - cohort)):
                if day and rng.random() > max(0.15, 0.8 - day * 0.08):
                    continue
                started = first + timedelta(days=day, seconds=index * 2)
                identity = f'{prefix}:{first.date()}:{index}:{day}'
                events = session_events(user, f'movie-{index % 5 + 1}', started,
                                        ['MOBILE', 'DESKTOP', 'TV', 'TABLET'][index % 4],
                                        complete=rng.random() < 0.8, identity=identity)
                search = MovieEvent(user_id=user, movie_id='', event_type='SEARCHED',
                                    timestamp=started - timedelta(seconds=10), device_type='MOBILE',
                                    session_id=identity, progress_seconds=0, search_query='comedy',
                                    event_id=uuid5(NAMESPACE_URL, identity + ':SEARCHED'))
                for event in [search, *events]:
                    publisher.publish(event)
                    count += 1
    return {'events': count, 'prefix': prefix}


async def generator_loop(publisher):
    needs_seed = os.getenv('DEMO_SEED', 'true').lower() == 'true'
    while True:
        try:
            if needs_seed:
                await asyncio.to_thread(generate_history, publisher, 8, 10, 'demo-v1')
                needs_seed = False
            elif os.getenv('GENERATE_EVENTS', 'true').lower() == 'true':
                now = datetime.now(timezone.utc)
                # Synthetic sessions are replayed at speed, with valid event-time ordering.
                events = session_events(f'live-{random.randrange(30)}', f'movie-{random.randrange(1, 6)}',
                                        now - timedelta(seconds=1300))
                for event in events:
                    await asyncio.to_thread(publisher.publish, event)
        except asyncio.CancelledError:
            raise
        except Exception:
            log.exception('generator publication failed; will retry next cycle')
        await asyncio.sleep(float(os.getenv('GENERATOR_INTERVAL_SECONDS', '5')))


@asynccontextmanager
async def lifespan(app):
    app.state.publisher = Publisher()
    task = asyncio.create_task(generator_loop(app.state.publisher))
    yield
    task.cancel()
    await asyncio.gather(task, return_exceptions=True)
    app.state.publisher.producer.flush(20)


app = FastAPI(title='Movie Service', lifespan=lifespan)


@app.get('/health')
def health():
    try:
        app.state.publisher.producer.list_topics(topic='movie-events', timeout=3)
    except KafkaException as exc:
        raise HTTPException(503, 'Kafka unavailable') from exc
    return {'status': 'ok', 'schema_version': 2}


@app.post('/events', status_code=201)
def publish(event: MovieEvent):
    try:
        return app.state.publisher.publish(event)
    except (KafkaException, BufferError) as exc:
        log.exception('publication failed event_id=%s', event.event_id)
        raise HTTPException(503, 'Kafka publication failed; retry with the same event_id') from exc


@app.post('/generate')
def generate(request: GenerateRequest):
    try:
        return generate_history(app.state.publisher, request.users, request.days)
    except (KafkaException, BufferError) as exc:
        raise HTTPException(503, 'Generation interrupted by Kafka failure') from exc
