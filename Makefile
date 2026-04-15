.PHONY: build up down rebuild logs

build:
	./mvnw clean package -DskipTests
	./mvnw docker:build -pl kafka-docker-producer
	./mvnw docker:build -pl kafka-docker-consumer

up: build
	docker-compose up -d

down:
	docker-compose down

rebuild:
	./mvnw clean package -DskipTests
	./mvnw docker:build -pl kafka-docker-producer
	./mvnw docker:build -pl kafka-docker-consumer
	docker-compose up -d --force-recreate producer consumer-btc consumer-ltc

logs:
	docker-compose logs -f producer consumer-btc consumer-ltc
