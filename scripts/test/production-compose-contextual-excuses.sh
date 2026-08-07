#!/usr/bin/env bash
set -Eeuo pipefail
IFS=$'\n\t'

repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
test_root="$(mktemp -d)"
trap 'rm -rf "$test_root"' EXIT

deployment_env="$test_root/deployment.env"
printf 'BOT_IMAGE=gridwords-bot:ci\n' > "$deployment_env"

render_compose() {
  local runtime_env="$1"
  docker compose \
    --project-name gridwords-compose-config-test \
    --env-file "$runtime_env" \
    --env-file "$deployment_env" \
    -f "$repo_root/compose.production.yaml" \
    config --format json
}

runtime_env="$test_root/runtime.env"
cp "$repo_root/scripts/test/production-runtime.test.env" "$runtime_env"
compose_json="$(render_compose "$runtime_env")"

printf '%s' "$compose_json" | python3 -c '
import json
import sys

environment = json.load(sys.stdin)["services"]["bot"]["environment"]
expected = {
    "EXCUSE_GENERATOR_CONTEXTUAL_ENABLED": "true",
    "EXCUSE_OFFER_LIFETIME": "PT17M",
    "EXCUSE_EXPIRATION_PAGE_SIZE": "17",
    "EXCUSE_EXPIRATION_MAX_PAGES": "3",
    "RECORD_BOOTSTRAP_POLL_DELAY": "PT7S",
    "RECORD_BOOTSTRAP_LEASE_DURATION": "PT37S",
    "RECORD_BOOTSTRAP_RETRY_BACKOFF": "PT11S",
    "RECORD_LIVE_EVALUATION_ENABLED": "false",
    "RECORD_LIVE_EVALUATION_POLL_DELAY": "PT3S",
    "RECORD_LIVE_EVALUATION_LEASE_DURATION": "PT41S",
    "RECORD_LIVE_EVALUATION_HEARTBEAT_INTERVAL": "PT13S",
    "RECORD_LIVE_EVALUATION_INITIAL_RETRY_BACKOFF": "PT5S",
    "RECORD_LIVE_EVALUATION_MAX_RETRY_BACKOFF": "PT43S",
    "RECORD_PUBLIC_ANNOUNCEMENTS_ENABLED": "true",
    "RECORD_ANNOUNCEMENT_POLL_DELAY": "PT4S",
    "RECORD_ANNOUNCEMENT_LEASE_DURATION": "PT47S",
    "RECORD_ANNOUNCEMENT_HEARTBEAT_INTERVAL": "PT17S",
    "RECORD_ANNOUNCEMENT_INITIAL_RETRY_BACKOFF": "PT6S",
    "RECORD_ANNOUNCEMENT_MAX_RETRY_BACKOFF": "PT53S",
}
for key, value in expected.items():
    actual = environment.get(key)
    if actual != value:
        raise SystemExit(f"{key}: expected {value!r}, got {actual!r}")
if "EXCUSES_ENABLED" in environment:
    raise SystemExit("obsolete EXCUSES_ENABLED unexpectedly reaches the production container")
'

fallback_env="$test_root/fallback.env"
cat > "$fallback_env" <<'EOF'
POSTGRES_DB=gridwords_test
POSTGRES_USER=gridwords_test
POSTGRES_PASSWORD=gridwords-test-password-not-a-secret
DATABASE_URL=jdbc:postgresql://postgres:5432/gridwords_test
DATABASE_USERNAME=gridwords_test
DATABASE_PASSWORD=gridwords-test-password-not-a-secret
DISCORD_ENABLED=false
DISCORD_GUILD_ID=100000000000000001
DISCORD_CHANNEL_ID=100000000000000002
ADMIN_USER_IDS=100000000000000003
EOF

fallback_json="$(render_compose "$fallback_env")"
printf '%s' "$fallback_json" | python3 -c '
import json
import sys

environment = json.load(sys.stdin)["services"]["bot"]["environment"]
expected = {
    "EXCUSE_GENERATOR_CONTEXTUAL_ENABLED": "false",
    "EXCUSE_OFFER_LIFETIME": "PT15M",
    "EXCUSE_EXPIRATION_PAGE_SIZE": "25",
    "EXCUSE_EXPIRATION_MAX_PAGES": "4",
    "RECORD_BOOTSTRAP_POLL_DELAY": "PT1M",
    "RECORD_BOOTSTRAP_LEASE_DURATION": "PT2M",
    "RECORD_BOOTSTRAP_RETRY_BACKOFF": "PT1M",
    "RECORD_LIVE_EVALUATION_ENABLED": "true",
    "RECORD_LIVE_EVALUATION_POLL_DELAY": "PT10S",
    "RECORD_LIVE_EVALUATION_LEASE_DURATION": "PT2M",
    "RECORD_LIVE_EVALUATION_HEARTBEAT_INTERVAL": "PT30S",
    "RECORD_LIVE_EVALUATION_INITIAL_RETRY_BACKOFF": "PT10S",
    "RECORD_LIVE_EVALUATION_MAX_RETRY_BACKOFF": "PT5M",
    "RECORD_PUBLIC_ANNOUNCEMENTS_ENABLED": "false",
    "RECORD_ANNOUNCEMENT_POLL_DELAY": "PT10S",
    "RECORD_ANNOUNCEMENT_LEASE_DURATION": "PT2M",
    "RECORD_ANNOUNCEMENT_HEARTBEAT_INTERVAL": "PT30S",
    "RECORD_ANNOUNCEMENT_INITIAL_RETRY_BACKOFF": "PT10S",
    "RECORD_ANNOUNCEMENT_MAX_RETRY_BACKOFF": "PT5M",
}
for key, value in expected.items():
    actual = environment.get(key)
    if actual != value:
        raise SystemExit(f"fallback {key}: expected {value!r}, got {actual!r}")
'

echo 'Production Compose passes contextual excuse and record settings with safe fallback defaults'
