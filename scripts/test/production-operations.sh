#!/usr/bin/env bash
set -Eeuo pipefail
IFS=$'\n\t'

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TEST_ROOT="$(mktemp -d)"
export ROOT_DIR="$REPO_ROOT"
export COMPOSE_FILE="$REPO_ROOT/compose.production.yaml"
export RUNTIME_ENV_FILE="$TEST_ROOT/runtime.env"
export DEPLOYMENT_ENV_FILE="$TEST_ROOT/deployment.env"
export DEPLOYMENT_STATE_FILE="$TEST_ROOT/deployment-state.env"
export OPERATION_LOCK_FILE="$TEST_ROOT/production-operation.lock"
export BACKUP_DIR="$TEST_ROOT/backups"
export COMPOSE_PROJECT_NAME="gridwords-ops-${GITHUB_RUN_ID:-$$}"
export IMAGE_REPOSITORY="gridwords-bot"
export SKIP_IMAGE_PULL=true
export HEALTH_RETRIES=30
export HEALTH_INTERVAL_SECONDS=2

cp "$REPO_ROOT/scripts/test/production-runtime.test.env" "$RUNTIME_ENV_FILE"
source "$REPO_ROOT/scripts/common.sh"

fail() { echo "TEST FAILURE: $*" >&2; exit 1; }
assert_eq() { [[ "$1" == "$2" ]] || fail "Expected '$2' but got '$1': $3"; }

cleanup() {
  set +e
  compose down -v --remove-orphans >/dev/null 2>&1
  rm -rf "$TEST_ROOT"
}
trap cleanup EXIT

for image in gridwords-bot:1.0.0 gridwords-bot:1.0.1 gridwords-bot:9.9.9; do
  docker image inspect "$image" >/dev/null 2>&1 || fail "Missing test image: $image"
done
[[ "$(image_id gridwords-bot:1.0.0)" != "$(image_id gridwords-bot:1.0.1)" ]] \
  || fail 'The two healthy release test images must have different image IDs'

BOT_IMAGE=gridwords-bot:1.0.0 compose config >/dev/null
compose down -v --remove-orphans >/dev/null 2>&1 || true

"$REPO_ROOT/scripts/deploy.sh" 1.0.0
"$REPO_ROOT/scripts/verify-deployment.sh"
assert_eq "$(deployment_value)" 'gridwords-bot:1.0.0' 'first deployment metadata'
assert_eq "$(state_value ACTIVE_IMAGE)" 'gridwords-bot:1.0.0' 'first deployment state'

postgres_user="$(runtime_value POSTGRES_USER)"
postgres_db="$(runtime_value POSTGRES_DB)"

sql() {
  local database="$1" statement="$2"
  compose exec -T postgres psql -U "$postgres_user" -d "$database" -v ON_ERROR_STOP=1 -tAc "$statement"
}

sql "$postgres_db" 'CREATE TABLE operations_test_marker (id integer PRIMARY KEY, value text NOT NULL);'
sql "$postgres_db" "INSERT INTO operations_test_marker (id, value) VALUES (1, 'before-backup');"
backup_path="$(BACKUP_FORCE_WEEKLY=true "$REPO_ROOT/scripts/backup.sh")"
[[ -s "$backup_path" ]] || fail 'Backup file is missing or empty'
[[ "$(find "$BACKUP_DIR" -maxdepth 1 -name 'weekly-gridwords-*.dump' | wc -l)" -eq 1 ]] \
  || fail 'Forced weekly backup was not created'

sql "$postgres_db" "UPDATE operations_test_marker SET value = 'after-backup' WHERE id = 1;"
"$REPO_ROOT/scripts/restore.sh" --to-empty-database gridwords_restore_test "$backup_path"
assert_eq "$(sql gridwords_restore_test 'SELECT value FROM operations_test_marker WHERE id = 1;')" \
  'before-backup' 'restore into separate empty database'

if "$REPO_ROOT/scripts/restore.sh" --to-empty-database "$postgres_db" "$backup_path"; then
  fail '--to-empty-database accepted the configured production database'
fi
assert_eq "$(sql "$postgres_db" 'SELECT value FROM operations_test_marker WHERE id = 1;')" \
  'after-backup' 'production database survived rejected test restore'

corrupt_dump="$TEST_ROOT/corrupt.dump"
printf 'not a PostgreSQL dump\n' > "$corrupt_dump"
if "$REPO_ROOT/scripts/restore.sh" --to-empty-database gridwords_corrupt_test "$corrupt_dump"; then
  fail 'Corrupt dump was accepted'
fi

compose stop bot
"$REPO_ROOT/scripts/restore.sh" --force "$backup_path"
assert_eq "$(sql "$postgres_db" 'SELECT value FROM operations_test_marker WHERE id = 1;')" \
  'before-backup' 'production restore'
"$REPO_ROOT/scripts/verify-deployment.sh"

retention_dir="$TEST_ROOT/retention"
mkdir -p "$retention_dir"
for age in $(seq 1 17); do
  file="$retention_dir/gridwords-retention-$age.dump"
  : > "$file"
  touch -d "$age days ago" "$file"
done
for age in $(seq 1 10); do
  file="$retention_dir/weekly-gridwords-retention-$age.dump"
  : > "$file"
  touch -d "$age weeks ago" "$file"
done
BACKUP_DIR="$retention_dir" DAILY_RETENTION=14 WEEKLY_RETENTION=8 \
  "$REPO_ROOT/scripts/backup.sh" --retention-only
assert_eq "$(find "$retention_dir" -maxdepth 1 -name 'gridwords-*.dump' | wc -l)" '14' 'daily retention'
assert_eq "$(find "$retention_dir" -maxdepth 1 -name 'weekly-gridwords-*.dump' | wc -l)" '8' 'weekly retention'

"$REPO_ROOT/scripts/deploy.sh" 1.0.1
"$REPO_ROOT/scripts/verify-deployment.sh"
assert_eq "$(deployment_value)" 'gridwords-bot:1.0.1' 'second deployment metadata'

if "$REPO_ROOT/scripts/deploy.sh" 9.9.9; then
  fail 'Deliberately unhealthy release reported success'
fi
"$REPO_ROOT/scripts/verify-deployment.sh"
assert_eq "$(deployment_value)" 'gridwords-bot:1.0.1' 'verified rollback metadata'
assert_eq "$(docker inspect "$(compose ps -q bot)" --format '{{.Config.Image}}')" \
  'gridwords-bot:1.0.1' 'verified rollback container'

# Simulate metadata written ahead of an interrupted deployment while the old image still runs.
printf 'BOT_IMAGE=gridwords-bot:1.0.0\n' > "$DEPLOYMENT_ENV_FILE"
"$REPO_ROOT/scripts/deploy.sh" 1.0.0
"$REPO_ROOT/scripts/verify-deployment.sh"
assert_eq "$(deployment_value)" 'gridwords-bot:1.0.0' 'resumed deployment metadata'
assert_eq "$(docker inspect "$(compose ps -q bot)" --format '{{.Config.Image}}')" \
  'gridwords-bot:1.0.0' 'resumed deployment container'

container_before="$(compose ps -q bot)"
"$REPO_ROOT/scripts/deploy.sh" 1.0.0
container_after="$(compose ps -q bot)"
assert_eq "$container_after" "$container_before" 'idempotent deployment must not recreate the bot'

compose restart postgres >/dev/null
wait_healthy postgres
"$REPO_ROOT/scripts/verify-deployment.sh"
assert_eq "$(sql "$postgres_db" 'SELECT value FROM operations_test_marker WHERE id = 1;')" \
  'before-backup' 'data persisted across PostgreSQL restart'

echo 'Production operations test completed successfully'
