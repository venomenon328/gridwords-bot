#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
require_file "$RUNTIME_ENV_FILE"
[[ "${1:-}" == --check ]] && { compose exec -T postgres pg_dump --version >/dev/null; exit 0; }
BACKUP_DIR="${BACKUP_DIR:-$ROOT_DIR/backups}"; mkdir -p "$BACKUP_DIR"; chmod 0700 "$BACKUP_DIR"
prune() { local pattern="$1" keep="$2"; mapfile -t files < <(find "$BACKUP_DIR" -maxdepth 1 -type f -name "$pattern" -printf '%T@ %p\n' | sort -nr | tail -n +$((keep + 1)) | cut -d' ' -f2-); for file in "${files[@]:-}"; do [[ -n "$file" ]] && rm -f -- "$file"; done; }
[[ "${1:-}" == --retention-only ]] && { prune 'gridwords-*.dump' "${DAILY_RETENTION:-14}"; prune 'weekly-gridwords-*.dump' "${WEEKLY_RETENTION:-8}"; exit 0; }
name="gridwords-$(date -u +%Y%m%dT%H%M%SZ).dump"; tmp="$BACKUP_DIR/.${name}.tmp.$$"; final="$BACKUP_DIR/$name"
compose exec -T postgres pg_dump -U "$(runtime_value POSTGRES_USER)" -d "$(runtime_value POSTGRES_DB)" --format=custom --no-owner > "$tmp"
docker run --rm --mount "type=bind,src=$BACKUP_DIR,dst=/backups,readonly" "$POSTGRES_IMAGE" pg_restore --list "/backups/$(basename "$tmp")" >/dev/null
mv -f "$tmp" "$final"; [[ "$(date +%u)" == 1 || "${BACKUP_FORCE_WEEKLY:-false}" == true ]] && cp -f "$final" "$BACKUP_DIR/weekly-gridwords-$(date +%G%V).dump"
prune 'gridwords-*.dump' "${DAILY_RETENTION:-14}"; prune 'weekly-gridwords-*.dump' "${WEEKLY_RETENTION:-8}"; echo "$final"
