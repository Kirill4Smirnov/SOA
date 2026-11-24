CREATE TABLE IF NOT EXISTS daily_metrics (
    metric_date date NOT NULL,
    metric_name text NOT NULL,
    dimension text NOT NULL DEFAULT '',
    value double precision,
    computed_at timestamptz NOT NULL,
    run_id uuid NOT NULL,
    PRIMARY KEY (metric_date, metric_name, dimension)
);

CREATE TABLE IF NOT EXISTS export_jobs (
    metric_date date PRIMARY KEY,
    status text NOT NULL CHECK (status IN ('pending', 'done')),
    last_error text,
    exported_at timestamptz
);
