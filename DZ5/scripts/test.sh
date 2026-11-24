#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

# Stop the entire test stack even if startup or tests fail. Keep data volumes.
trap 'docker compose --profile test down' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

docker compose up --build -d --wait
if [[ "${1:-}" == "--failover" ]]; then
    docker compose stop kafka-1
    docker compose run --rm --no-deps tests python -m pytest -q -p no:cacheprovider tests/test_pipeline.py
else
    docker compose run --rm --no-deps tests
fi
