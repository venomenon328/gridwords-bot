#!/usr/bin/env bash
set -Eeuo pipefail
IFS=$'\n\t'

repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
test_root="$(mktemp -d)"
trap 'rm -rf "$test_root"' EXIT

runtime_env="$test_root/runtime.env"
deployment_env="$test_root/deployment.env"
cp "$repo_root/scripts/test/production-runtime.test.env" "$runtime_env"
printf 'BOT_IMAGE=gridwords-bot:ci\n' > "$deployment_env"

compose_json="$(
  docker compose \
    --project-name gridwords-compose-config-test \
    --env-file "$runtime_env" \
    --env-file "$deployment_env" \
    -f "$repo_root/compose.production.yaml" \
    config --format json
)"

printf '%s' "$compose_json" | python3 -c '
import json
import sys

environment = json.load(sys.stdin)["services"]["bot"]["environment"]
expected = {
    "EXCUSE_GENERATOR_CONTEXTUAL_ENABLED": "true",
    "EXCUSE_OFFER_LIFETIME": "PT17M",
    "EXCUSE_EXPIRATION_PAGE_SIZE": "17",
    "EXCUSE_EXPIRATION_MAX_PAGES": "3",
}
for key, value in expected.items():
    actual = environment.get(key)
    if actual != value:
        raise SystemExit(f"{key}: expected {value!r}, got {actual!r}")
if "EXCUSES_ENABLED" in environment:
    raise SystemExit("obsolete EXCUSES_ENABLED unexpectedly reaches the production container")
'

echo 'Production Compose passes contextual excuse settings to the bot container'
