#!/usr/bin/env bash
set -Eeuo pipefail
IFS=$'\n\t'

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${COMPOSE_FILE:-$ROOT_DIR/compose.production.yaml}"
RUNTIME_ENV_FILE="${RUNTIME_ENV_FILE:-$ROOT_DIR/runtime.env}"
DEPLOYMENT_ENV_FILE="${DEPLOYMENT_ENV_FILE:-$ROOT_DIR/deployment.env}"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-gridwords-production}"
POSTGRES_IMAGE="${POSTGRES_IMAGE:-postgres:16.14-alpine@sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777}"

die() { echo "ERROR: $*" >&2; exit 1; }
require_file() { [[ -f "$1" ]] || die "Required file is missing: $1"; }
runtime_value() { grep -E "^$1=" "$RUNTIME_ENV_FILE" | tail -n 1 | cut -d= -f2-; }
deployment_value() { [[ -f "$DEPLOYMENT_ENV_FILE" ]] && grep -E '^BOT_IMAGE=' "$DEPLOYMENT_ENV_FILE" | tail -n 1 | cut -d= -f2- || true; }
compose() { docker compose --project-name "$COMPOSE_PROJECT_NAME" --env-file "$RUNTIME_ENV_FILE" --env-file "$DEPLOYMENT_ENV_FILE" -f "$COMPOSE_FILE" "$@"; }
compose_with_image() { BOT_IMAGE="$1" compose "${@:2}"; }
valid_tag() { [[ "$1" =~ ^sha-[0-9a-f]{40}$ || "$1" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; }
write_deployment() { local image="$1" tmp="${DEPLOYMENT_ENV_FILE}.tmp.$$"; umask 077; printf 'BOT_IMAGE=%s\n' "$image" > "$tmp"; mv -f "$tmp" "$DEPLOYMENT_ENV_FILE"; }
wait_healthy() { local service="$1" id status; for _ in $(seq 1 "${HEALTH_RETRIES:-36}"); do id="$(compose ps -q "$service")"; [[ -n "$id" ]] || { sleep 5; continue; }; status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$id")"; [[ "$status" == healthy ]] && return; sleep 5; done; die "$service did not become healthy"; }
