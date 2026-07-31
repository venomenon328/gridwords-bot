#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
require_file "$RUNTIME_ENV_FILE"; require_file "$DEPLOYMENT_ENV_FILE"
expected="$(deployment_value)"; [[ -n "$expected" ]] || die 'BOT_IMAGE is missing'
wait_healthy postgres; wait_healthy bot
[[ "$(docker inspect "$(compose ps -q bot)" --format '{{.Config.Image}}')" == "$expected" ]] || die 'Unexpected active bot image'
[[ "$(docker inspect "$(compose ps -q postgres)" --format '{{json .NetworkSettings.Ports}}')" == *'"5432/tcp":null'* ]] || die 'PostgreSQL has a host port'
compose exec -T postgres pg_isready -U "$(runtime_value POSTGRES_USER)" -d "$(runtime_value POSTGRES_DB)" >/dev/null
compose exec -T bot curl --fail --silent http://127.0.0.1:8081/actuator/health/readiness >/dev/null
[[ "$(compose exec -T postgres psql -U "$(runtime_value POSTGRES_USER)" -d "$(runtime_value POSTGRES_DB)" -tAc 'select count(*) from databasechangelog')" -gt 0 ]] || die 'Liquibase changelog is empty'
"$(dirname "$0")/backup.sh" --check; echo "Deployment verified: $expected"
