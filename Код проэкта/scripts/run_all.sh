#!/bin/bash
# Запуск всего сценария: Redis -> заполнение -> демо -> тесты
docker start redis-sro 2>/dev/null || docker compose up -d
mvn -q compile
mvn -q exec:java -Dexec.mainClass=kz.sro.sessions.Seed
mvn -q exec:java -Dexec.mainClass=kz.sro.sessions.Demo
mvn -q test