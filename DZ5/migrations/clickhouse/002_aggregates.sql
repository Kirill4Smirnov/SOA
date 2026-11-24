CREATE TABLE IF NOT EXISTS cinema.aggregate_values
(
    metric_date Date,
    metric_name LowCardinality(String),
    dimension String,
    value Nullable(Float64),
    computed_at DateTime64(6, 'UTC'),
    run_id UUID
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(metric_date)
ORDER BY (metric_date, run_id, metric_name, dimension);

CREATE TABLE IF NOT EXISTS cinema.aggregate_runs
(
    metric_date Date,
    run_id UUID,
    computed_at DateTime64(6, 'UTC'),
    source_events UInt64
)
ENGINE = MergeTree
ORDER BY (metric_date, computed_at, run_id);

CREATE VIEW IF NOT EXISTS cinema.metrics_current AS
SELECT v.metric_date, v.metric_name, v.dimension, v.value, v.computed_at
FROM cinema.aggregate_values AS v
INNER JOIN
(
    SELECT metric_date, argMax(run_id, computed_at) AS run_id
    FROM cinema.aggregate_runs
    GROUP BY metric_date
) AS latest USING (metric_date, run_id);
