#!/usr/bin/env bash
set -Eeuo pipefail
IFS=$'\n\t'

ROOT_DIR="${ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
COMPOSE_FILE="${COMPOSE_FILE:-$ROOT_DIR/compose.production.yaml}"
RUNTIME_ENV_FILE="${RUNTIME_ENV_FILE:-$ROOT_DIR/runtime.env}"
DEPLOYMENT_ENV_FILE="${DEPLOYMENT_ENV_FILE:-$ROOT_DIR/deployment.env}"
DEPLOYMENT_STATE_FILE="${DEPLOYMENT_STATE_FILE:-$ROOT_DIR/deployment-state.env}"
OPERATION_LOCK_FILE="${OPERATION_LOCK_FILE:-$ROOT_DIR/.production-operation.lock}"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-gridwords-production}"
POSTGRES_IMAGE="${POSTGRES_IMAGE:-postgres:16.14-alpine@sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777}"

die() { echo "ERROR: $*" >&2; exit 1; }
require_file() { [[ -f "$1" ]] || die "Required file is missing: $1"; }
runtime_value() { grep -E "^$1=" "$RUNTIME_ENV_FILE" | tail -n 1 | cut -d= -f2-; }

deployment_value() {
  if [[ -f "$DEPLOYMENT_ENV_FILE" ]]; then
    grep -E '^BOT_IMAGE=' "$DEPLOYMENT_ENV_FILE" | tail -n 1 | cut -d= -f2-
  fi
}

state_value() {
  if [[ -f "$DEPLOYMENT_STATE_FILE" ]]; then
    grep -E "^$1=" "$DEPLOYMENT_STATE_FILE" | tail -n 1 | cut -d= -f2-
  fi
}

compose() {
  local image="${BOT_IMAGE:-$(deployment_value)}"
  local -a args=(docker compose --project-name "$COMPOSE_PROJECT_NAME" --env-file "$RUNTIME_ENV_FILE")
  if [[ -f "$DEPLOYMENT_ENV_FILE" ]]; then
    args+=(--env-file "$DEPLOYMENT_ENV_FILE")
  fi
  args+=(-f "$COMPOSE_FILE")
  BOT_IMAGE="${image:-gridwords-bot:unconfigured}" "${args[@]}" "$@"
}

compose_with_image() {
  local image="$1"
  shift
  BOT_IMAGE="$image" compose "$@"
}

valid_tag() { [[ "$1" =~ ^sha-[0-9a-f]{40}$ || "$1" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; }

write_deployment() {
  local image="$1" tmp="${DEPLOYMENT_ENV_FILE}.tmp.$$"
  umask 077
  printf 'BOT_IMAGE=%s\n' "$image" > "$tmp"
  mv -f "$tmp" "$DEPLOYMENT_ENV_FILE"
}

image_id() { docker image inspect "$1" --format '{{.Id}}'; }

image_repo_digest() {
  local image="$1" repository digest
  repository="${image%:*}"
  while IFS= read -r digest; do
    if [[ "$digest" == "$repository@"* ]]; then
      printf '%s\n' "$digest"
      return 0
    fi
  done < <(docker image inspect "$image" --format '{{range .RepoDigests}}{{println .}}{{end}}')
}

acquire_operation_lock() {
  mkdir -p "$(dirname "$OPERATION_LOCK_FILE")"
  exec 8>"$OPERATION_LOCK_FILE"
  flock -n 8 || die 'Another production deployment or restore is already running'
}

service_running() {
  local id
  id="$(compose ps -q "$1")"
  if [[ -z "$id" ]]; then
    return 1
  fi
  [[ "$(docker inspect --format '{{.State.Running}}' "$id")" == true ]]
}

wait_healthy() {
  local service="$1" id status
  for _ in $(seq 1 "${HEALTH_RETRIES:-36}"); do
    id="$(compose ps -q "$service")"
    if [[ -n "$id" ]]; then
      status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$id")"
      if [[ "$status" == healthy ]]; then
        return
      fi
      if [[ "$status" == exited || "$status" == dead ]]; then
        die "$service stopped before becoming healthy"
      fi
    fi
    sleep "${HEALTH_INTERVAL_SECONDS:-5}"
  done
  die "$service did not become healthy"
}
