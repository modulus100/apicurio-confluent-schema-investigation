.PHONY: buf-deps buf-lint buf-generate up down java-producer python-producer

buf-deps:
	buf dep update

buf-lint:
	buf lint

buf-generate:
	buf generate

up:
	docker compose up -d

down:
	docker compose down

java-producer:
	./gradlew :java-producer:run

python-producer:
	cd python-producer && uv sync && uv run producer.py
