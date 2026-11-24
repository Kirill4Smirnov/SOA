.PHONY: up down test logs resilience

up:
	docker compose up --build

down:
	docker compose --profile test down

test:
	bash scripts/test.sh

logs:
	docker compose logs -f movie-service aggregation-service

resilience:
	bash scripts/test-failover.sh
