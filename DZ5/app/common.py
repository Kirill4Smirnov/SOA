import logging
import os
import time
from pathlib import Path

import boto3
import httpx
import psycopg
from botocore.config import Config

logging.basicConfig(level=logging.INFO, format='%(asctime)s %(levelname)s %(name)s %(message)s')
logging.getLogger('httpx').setLevel(logging.WARNING)
ROOT = Path(__file__).resolve().parent.parent
log = logging.getLogger('cinema')


def pg():
    return psycopg.connect(os.environ['POSTGRES_DSN'], connect_timeout=5)


def ch(sql, parameters=None):
    params = {f'param_{key}': str(value) for key, value in (parameters or {}).items()}
    response = httpx.post(
        os.environ['CLICKHOUSE_URL'], params=params, content=sql.encode(),
        auth=(os.environ['CLICKHOUSE_USER'], os.environ['CLICKHOUSE_PASSWORD']), timeout=120,
    )
    try:
        response.raise_for_status()
    except httpx.HTTPStatusError as exc:
        raise RuntimeError(f'ClickHouse query failed: {response.text.strip()}') from exc
    return response


def ch_rows(sql, parameters=None):
    return ch(sql + ' FORMAT JSON', parameters).json()['data']


def s3():
    return boto3.client('s3', endpoint_url=os.environ['S3_ENDPOINT_URL'], region_name='us-east-1',
                        config=Config(connect_timeout=3, read_timeout=10, retries={'max_attempts': 2},
                                      request_checksum_calculation='when_required',
                                      response_checksum_validation='when_required'))


def retry(operation, exceptions, attempts=3):
    for attempt in range(attempts):
        try:
            return operation()
        except exceptions:
            if attempt + 1 == attempts:
                raise
            delay = 0.2 * 2 ** attempt
            log.warning('retry attempt=%d delay=%.1f', attempt + 1, delay, exc_info=True)
            time.sleep(delay)
