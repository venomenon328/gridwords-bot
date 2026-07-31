#!/usr/bin/env bash
set -Eeuo pipefail
# shellcheck source=common.sh
source "$(dirname "$0")/common.sh"

force=false
target=''
dump=''
while [[ $# -gt 0 ]]; do
  case "$1" in
    --force)
      force=true
      ;;
    --to-empty-database)
      shift
      [[ $# -gt 0 ]] || die 'Missing database name after --to-empty-database'
      target="$1"
      ;;
    --*)
      die "Unknown option: $1"
      ;;
    *)
      [[ -z "$dump" ]] || die 'Only one backup dump may be supplied'
      dump="$1"
      ;;
  esac
  shift
done

[[ -n "$dump" && -f "$dump" ]] \
  || die 'Usage: restore.sh --force <backup.dump> or restore.sh --to-empty-database <database> <backup.dump>'
require_file "$RUNTIME_ENV_FILE"
acquire_operation_lock

db="$(runtime_value POSTGRES_DB)"
user="$(runtime_value POSTGRES_USER)"
[[ "$db" =~ ^[a-zA-Z0-9_]+$ ]] || die 'Configured production database name is unsafe'
[[ -z "$target" || "$target" =~ ^[a-zA-Z0-9_]+$ ]] || die 'Invalid target database name'

production_restore=false
if [[ -n "$target" ]]; then
  [[ "$target" != "$db" ]] \
    || die '--to-empty-database may never target the configured production database; use --force with the bot stopped'
  [[ "$target" != postgres && "$target" != template0 && "$target" != template1 ]] \
    || die 'Refusing to replace a PostgreSQL maintenance database'
else
  [[ "$force" == true ]] || die 'Refusing destructive production restore without --force'
  service_running bot && die 'Stop the bot before a production restore'
  production_restore=true
  target="$db"
fi

backup_dir="$(cd "$(dirname "$dump")" && pwd)"
dump_name="$(basename "$dump")"
docker run --rm \
  --mount "type=bind,src=$backup_dir,dst=/backups,readonly" \
  "$POSTGRES_IMAGE" \
  pg_restore --list "/backups/$dump_name" >/dev/null

wait_healthy postgres
if [[ "$production_restore" == true ]]; then
  "$(dirname "$0")/backup.sh" >/dev/null
fi

compose exec -T postgres psql -U "$user" -d postgres -v ON_ERROR_STOP=1 \
  -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$target' AND pid <> pg_backend_pid();" \
  -c "DROP DATABASE IF EXISTS \"$target\";" \
  -c "CREATE DATABASE \"$target\" OWNER \"$user\";"
compose exec -T postgres pg_restore \
  -U "$user" -d "$target" \
  --no-owner --no-privileges --exit-on-error < "$dump"

if [[ "$production_restore" == true ]]; then
  compose up -d bot
  "$(dirname "$0")/verify-deployment.sh"
fi

echo "Restore complete: $target"
