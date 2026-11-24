CREATE TABLE IF NOT EXISTS cinema.raw_events
(
    event_id UUID,
    user_id String,
    movie_id String,
    event_type Enum8('VIEW_STARTED'=1, 'VIEW_FINISHED'=2, 'VIEW_PAUSED'=3, 'VIEW_RESUMED'=4, 'LIKED'=5, 'SEARCHED'=6),
    timestamp DateTime64(3, 'UTC'),
    device_type Enum8('MOBILE'=1, 'DESKTOP'=2, 'TV'=3, 'TABLET'=4),
    session_id String,
    progress_seconds Int32,
    search_query Nullable(String),
    ingested_at DateTime64(6, 'UTC') DEFAULT now64(6)
)
ENGINE = ReplacingMergeTree(ingested_at)
PARTITION BY toYYYYMM(timestamp)
ORDER BY (toDate(timestamp), user_id, event_id);

CREATE TABLE IF NOT EXISTS cinema.kafka_events
(
    event_id UUID,
    user_id String,
    movie_id String,
    event_type Enum8('VIEW_STARTED'=1, 'VIEW_FINISHED'=2, 'VIEW_PAUSED'=3, 'VIEW_RESUMED'=4, 'LIKED'=5, 'SEARCHED'=6),
    timestamp DateTime64(3, 'UTC'),
    device_type Enum8('MOBILE'=1, 'DESKTOP'=2, 'TV'=3, 'TABLET'=4),
    session_id String,
    progress_seconds Int32,
    search_query Nullable(String)
)
ENGINE = Kafka
SETTINGS kafka_broker_list='kafka-1:9092,kafka-2:9092,kafka-3:9092',
    kafka_topic_list='movie-events', kafka_group_name='clickhouse-cinema-v1',
    kafka_format='AvroConfluent', kafka_num_consumers=3,
    kafka_thread_per_consumer=1, kafka_flush_interval_ms=1000,
    format_avro_schema_registry_url='http://schema-registry:8080/apis/ccompat/v7',
    input_format_avro_allow_missing_fields=1;

CREATE MATERIALIZED VIEW IF NOT EXISTS cinema.ingest_events
TO cinema.raw_events AS
SELECT event_id, user_id, movie_id, event_type, timestamp,
       device_type, session_id, progress_seconds, search_query
FROM cinema.kafka_events;
