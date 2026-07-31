#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"

[[ $# -le 1 ]] || die 'Usage: verify-deployment.sh [expected-image]'
require_file "$RUNTIME_ENV_FILE"
explicit_expected=false
if [[ $# == 1 ]]; then
  expected="$1"
  explicit_expected=true
else
  require_file "$DEPLOYMENT_ENV_FILE"
  expected="$(deployment_value)"
fi
[[ -n "$expected" ]] || die 'BOT_IMAGE is missing'

wait_healthy postgres
wait_healthy bot

bot_id="$(compose ps -q bot)"
postgres_id="$(compose ps -q postgres)"
[[ -n "$bot_id" && -n "$postgres_id" ]] || die 'Expected production containers are missing'

actual_configured_image="$(docker inspect "$bot_id" --format '{{.Config.Image}}')"
actual_image_id="$(docker inspect "$bot_id" --format '{{.Image}}')"
expected_image_id="$(image_id "$expected")"
[[ "$actual_configured_image" == "$expected" ]] || die "Unexpected active bot image: $actual_configured_image"
[[ "$actual_image_id" == "$expected_image_id" ]] || die 'Active bot container does not use the locally resolved expected image'
[[ -z "$(docker port "$postgres_id")" ]] || die 'PostgreSQL has a host port'
[[ -z "$(docker port "$bot_id")" ]] || die 'Bot or management endpoint has a host port'

compose exec -T postgres pg_isready -U "$(runtime_value POSTGRES_USER)" -d "$(runtime_value POSTGRES_DB)" >/dev/null
compose exec -T bot curl --fail --silent http://127.0.0.1:8081/actuator/health/liveness >/dev/null
compose exec -T bot curl --fail --silent http://127.0.0.1:8081/actuator/health/readiness >/dev/null
[[ "$(compose exec -T postgres psql -U "$(runtime_value POSTGRES_USER)" -d "$(runtime_value POSTGRES_DB)" -tAc 'select count(*) from databasechangelog')" -gt 0 ]] \
  || die 'Liquibase changelog is empty'
"$(dirname "$0")/backup.sh" --check

if [[ "$explicit_expected" == false ]]; then
  require_file "$DEPLOYMENT_STATE_FILE"
  [[ "$(state_value ACTIVE_IMAGE)" == "$expected" ]] || die 'Deployment state records a different active image'
  [[ "$(state_value ACTIVE_IMAGE_ID)" == "$actual_image_id" ]] || die 'Deployment state image ID does not match the running container'
  recorded_repo_digest="$(state_value ACTIVE_REPO_DIGEST)"
  if [[ -n "$recorded_repo_digest" ]]; then
    image_repo_digest "$expected" | grep -Fx -- "$recorded_repo_digest" >/dev/null \
      || die 'Deployment state repository digest does not match the resolved image'
  fi
fi

echo "Deployment verified: $expected ($actual_image_id)"
