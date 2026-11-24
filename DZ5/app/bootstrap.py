import hashlib
import json
import os

import httpx
from confluent_kafka.admin import AdminClient, NewTopic

from app.common import ROOT, ch, ch_rows, log, pg, s3


def migrate():
    with pg() as conn:
        conn.execute('SELECT pg_advisory_xact_lock(5000)')
        conn.execute('CREATE TABLE IF NOT EXISTS schema_migrations (version text PRIMARY KEY, checksum text NOT NULL)')
        for path in sorted((ROOT / 'migrations/postgres').glob('*.sql')):
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            row = conn.execute('SELECT checksum FROM schema_migrations WHERE version=%s', (path.name,)).fetchone()
            if row:
                if row[0] != digest:
                    raise RuntimeError(f'Migration changed: {path.name}')
                continue
            conn.execute(path.read_text())
            conn.execute('INSERT INTO schema_migrations VALUES (%s,%s)', (path.name, digest))
            log.info('postgres migration=%s', path.name)
    ch('CREATE TABLE IF NOT EXISTS cinema.schema_migrations (version String, checksum String) ENGINE=MergeTree ORDER BY version')
    applied = {row['version']: row['checksum'] for row in ch_rows('SELECT * FROM cinema.schema_migrations')}
    for path in sorted((ROOT / 'migrations/clickhouse').glob('*.sql')):
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        if path.name in applied:
            if applied[path.name] != digest:
                raise RuntimeError(f'Migration changed: {path.name}')
            continue
        for statement in path.read_text().split(';'):
            if statement.strip():
                ch(statement)
        ch('INSERT INTO cinema.schema_migrations FORMAT JSONEachRow\n' +
           json.dumps({'version': path.name, 'checksum': digest}))
        log.info('clickhouse migration=%s', path.name)


def main():
    admin = AdminClient({'bootstrap.servers': os.environ['KAFKA_BOOTSTRAP_SERVERS']})
    topics = admin.list_topics(timeout=20).topics
    if 'movie-events' not in topics:
        futures = admin.create_topics([NewTopic('movie-events', num_partitions=3, replication_factor=3,
                                               config={'min.insync.replicas': '2', 'retention.ms': '604800000'})])
        futures['movie-events'].result(30)
    registry = os.environ['SCHEMA_REGISTRY_URL']
    with httpx.Client(base_url=registry, timeout=15) as client:
        client.put('/config', json={'compatibility': 'BACKWARD_TRANSITIVE'}).raise_for_status()
        for version in (1, 2):
            schema = (ROOT / f'schemas/movie-event-v{version}.avsc').read_text()
            response = client.post('/subjects/movie-events-value/versions', json={'schema': schema, 'schemaType': 'AVRO'})
            response.raise_for_status()
            log.info('registered schema=v%s id=%s', version, response.json()['id'])
    migrate()
    storage = s3()
    bucket = os.environ['S3_BUCKET']
    if bucket not in [item['Name'] for item in storage.list_buckets()['Buckets']]:
        storage.create_bucket(Bucket=bucket)
    log.info('bootstrap complete')


if __name__ == '__main__':
    main()
