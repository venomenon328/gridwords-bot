#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
[[ $# == 1 ]] || die 'Usage: deploy.sh <sha-40hex|MAJOR.MINOR.PATCH>'
valid_tag "$1" || die 'Image tag must be sha-<40 lowercase hex> or MAJOR.MINOR.PATCH'
require_file "$RUNTIME_ENV_FILE"; target="${IMAGE_REPOSITORY:-ghcr.io/venomenon328/gridwords-bot}:$1"; previous="$(deployment_value)"
[[ "$target" == "$previous" ]] && { "$(dirname "$0")/verify-deployment.sh"; echo 'Deployment already active'; exit 0; }
compose_with_image "$target" up -d postgres; wait_healthy postgres
"$(dirname "$0")/backup.sh" >/dev/null
[[ "${SKIP_IMAGE_PULL:-false}" == true ]] || docker pull "$target"
write_deployment "$target"
if ! compose up -d --no-deps bot || ! "$(dirname "$0")/verify-deployment.sh"; then
  [[ -n "$previous" ]] && { write_deployment "$previous"; compose up -d --no-deps bot || true; }
  die 'Deployment failed; previous app image was retained or restored'
fi
digest="$(docker image inspect "$target" --format '{{.Id}}')"; state="$ROOT_DIR/deployment-state.env"; tmp="$state.tmp.$$"; umask 077
printf 'ACTIVE_IMAGE=%s\nACTIVE_DIGEST=%s\nPREVIOUS_IMAGE=%s\nDEPLOYED_AT=%s\n' "$target" "$digest" "$previous" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$tmp"; mv -f "$tmp" "$state"
echo "Deployment complete: $target ($digest)"
