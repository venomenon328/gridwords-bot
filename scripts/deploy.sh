#!/usr/bin/env bash
set -Eeuo pipefail
# shellcheck source=common.sh
source "$(dirname "$0")/common.sh"

[[ $# == 1 ]] || die 'Usage: deploy.sh <sha-40hex|MAJOR.MINOR.PATCH>'
valid_tag "$1" || die 'Image tag must be sha-<40 lowercase hex> or MAJOR.MINOR.PATCH'
require_file "$RUNTIME_ENV_FILE"
acquire_operation_lock

target="${IMAGE_REPOSITORY:-ghcr.io/venomenon328/gridwords-bot}:$1"
recorded_previous="$(deployment_value)"
bot_id="$(compose ps -q bot)"
running_previous=''
if [[ -n "$bot_id" ]]; then
  running_previous="$(docker inspect "$bot_id" --format '{{.Config.Image}}')"
fi
rollback_image="${running_previous:-$recorded_previous}"
verify_script="$(dirname "$0")/verify-deployment.sh"

write_state() {
  local active="$1" previous="$2" tmp="${DEPLOYMENT_STATE_FILE}.tmp.$$"
  local local_id repo_digest
  local_id="$(image_id "$active")"
  repo_digest="$(image_repo_digest "$active")"
  umask 077
  printf 'ACTIVE_IMAGE=%s\nACTIVE_IMAGE_ID=%s\nACTIVE_REPO_DIGEST=%s\nPREVIOUS_IMAGE=%s\nDEPLOYED_AT=%s\n' \
    "$active" "$local_id" "$repo_digest" "$previous" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$tmp"
  mv -f "$tmp" "$DEPLOYMENT_STATE_FILE"
}

if [[ "$target" == "$recorded_previous" && -n "$bot_id" ]] \
    && [[ "$(docker inspect "$bot_id" --format '{{.Config.Image}}')" == "$target" ]] \
    && "$verify_script" "$target"; then
  if "$verify_script" >/dev/null 2>&1; then
    echo 'Deployment already active and healthy'
  else
    recovered_previous="$(state_value ACTIVE_IMAGE)"
    if [[ -z "$recovered_previous" || "$recovered_previous" == "$target" ]]; then
      recovered_previous="$(state_value PREVIOUS_IMAGE)"
    fi
    write_state "$target" "$recovered_previous"
    "$verify_script"
    echo 'Deployment was healthy; missing or stale confirmation metadata was reconciled'
  fi
  exit 0
fi

compose_with_image "$target" up -d postgres
BOT_IMAGE="$target" wait_healthy postgres
backup_path=''
if ! backup_path="$(BOT_IMAGE="$target" "$(dirname "$0")/backup.sh")"; then
  die 'Pre-deployment database backup failed'
fi
echo "Pre-deployment backup verified: $backup_path"
[[ "${SKIP_IMAGE_PULL:-false}" == true ]] || docker pull "$target"

if compose_with_image "$target" up -d --no-deps bot && "$verify_script" "$target"; then
  write_deployment "$target"
  write_state "$target" "$rollback_image"
  "$verify_script"
  echo "Deployment complete: $target ($(image_id "$target"))"
  exit 0
fi

echo "Deployment of $target failed; attempting verified rollback" >&2
if [[ -n "$rollback_image" && "$rollback_image" != "$target" ]] \
    && compose_with_image "$rollback_image" up -d --no-deps bot \
    && "$verify_script" "$rollback_image"; then
  write_deployment "$rollback_image"
  write_state "$rollback_image" "$target"
  "$verify_script"
  die "Deployment failed; previous app image was restored and verified: $rollback_image"
fi

compose_with_image "$target" rm -sf bot >/dev/null 2>&1 || true
die 'Deployment failed and no healthy previous app image could be restored'
