#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
require_file "$RUNTIME_ENV_FILE"

if [[ "${1:-}" == --check ]]; then
  compose exec -T postgres pg_dump --version >/dev/null
  exit 0
fi

BACKUP_DIR="${BACKUP_DIR:-$ROOT_DIR/backups}"
mkdir -p "$BACKUP_DIR"
BACKUP_DIR="$(cd "$BACKUP_DIR" && pwd)"
chmod 0700 "$BACKUP_DIR"
umask 077
exec 9>"$BACKUP_DIR/.backup.lock"
flock -n 9 || die 'Another backup is already running'

prune() {
  local pattern="$1" keep="$2" file
  while IFS= read -r file; do
    [[ -n "$file" ]] && rm -f -- "$file"
  done < <(find "$BACKUP_DIR" -maxdepth 1 -type f -name "$pattern" -printf '%T@ %p\n' \
    | sort -nr | tail -n +$((keep + 1)) | cut -d' ' -f2-)
}

if [[ "${1:-}" == --retention-only ]]; then
  prune 'gridwords-*.dump' "${DAILY_RETENTION:-14}"
  prune 'weekly-gridwords-*.dump' "${WEEKLY_RETENTION:-8}"
  exit 0
fi

name="gridwords-$(date -u +%Y%m%dT%H%M%S%NZ).dump"
tmp="$BACKUP_DIR/.${name}.tmp.$$"
final="$BACKUP_DIR/$name"
cleanup() {
  if [[ -n "${tmp:-}" ]]; then
    rm -f -- "$tmp"
  fi
  :
}
trap cleanup EXIT

compose exec -T postgres pg_dump \
  -U "$(runtime_value POSTGRES_USER)" \
  -d "$(runtime_value POSTGRES_DB)" \
  --format=custom --no-owner > "$tmp"
docker run --rm \
  --mount "type=bind,src=$BACKUP_DIR,dst=/backups,readonly" \
  "$POSTGRES_IMAGE" \
  pg_restore --list "/backups/$(basename "$tmp")" >/dev/null
mv -f "$tmp" "$final"
tmp=''

if [[ "$(TZ=Europe/Berlin date +%u)" == 1 || "${BACKUP_FORCE_WEEKLY:-false}" == true ]]; then
  weekly="$BACKUP_DIR/weekly-gridwords-$(TZ=Europe/Berlin date +%G%V).dump"
  weekly_tmp="${weekly}.tmp.$$"
  cp "$final" "$weekly_tmp"
  mv -f "$weekly_tmp" "$weekly"
fi

prune 'gridwords-*.dump' "${DAILY_RETENTION:-14}"
prune 'weekly-gridwords-*.dump' "${WEEKLY_RETENTION:-8}"
echo "$final"
