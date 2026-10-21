.PHONY: generate test build up down demo db

MAVEN_IMAGE := maven:3.9.9-eclipse-temurin-21-alpine

generate:
	docker run --rm -v "$(CURDIR):/src" -w /src $(MAVEN_IMAGE) mvn generate-sources

test:
	docker run --rm -v "$(CURDIR):/src" -w /src $(MAVEN_IMAGE) mvn test

build:
	docker compose build

up:
	docker compose up --build -d

down:
	docker compose down

demo:
	./scripts/demo.sh

db:
	docker compose exec postgres psql -U marketplace -d marketplace
