.PHONY: up down build test demo logs

up:
	docker compose up --build -d

down:
	docker compose down

build:
	mvn clean package

test:
	mvn test

demo:
	./scripts/demo.sh

logs:
	docker compose logs -f booking-service flight-service redis-sentinel

