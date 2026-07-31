#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
force=false; target=''; dump=''
while [[ $# -gt 0 ]]; do case "$1" in --force) force=true;; --to-empty-database) shift; target="${1:-}";; *) dump="$1";; esac; shift; done
[[ -f "$dump" ]] || die 'Usage: restore.sh --force <backup.dump> or restore.sh --to-empty-database <database> <backup.dump>'
docker run --rm --mount "type=bind,src=$(cd "$(dirname "$dump")" && pwd),dst=/backups,readonly" "$POSTGRES_IMAGE" pg_restore --list "/backups/$(basename "$dump")" >/dev/null
require_file "$RUNTIME_ENV_FILE"; db="$(runtime_value POSTGRES_DB)"; user="$(runtime_value POSTGRES_USER)"
if [[ -z "$target" ]]; then [[ "$force" == true ]] || die 'Refusing destructive production restore without --force'; [[ "$(docker inspect "$(compose ps -q bot)" --format '{{.State.Running}}')" == false ]] || die 'Stop bot before production restore'; "$(dirname "$0")/backup.sh" >/dev/null; target="$db"; fi
[[ "$target" =~ ^[a-zA-Z0-9_]+$ ]] || die 'Invalid target database name'
compose exec -T postgres psql -U "$user" -d postgres -v ON_ERROR_STOP=1 -c "DROP DATABASE IF EXISTS \"$target\";" -c "CREATE DATABASE \"$target\" OWNER \"$user\";"
cat "$dump" | compose exec -T postgres pg_restore -U "$user" -d "$target" --no-owner --no-privileges --exit-on-error
if [[ "$target" == "$db" ]]; then compose up -d bot; "$(dirname "$0")/verify-deployment.sh"; fi
echo "Restore complete: $target"
