#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
envfile=${ENV_FILE:-.env.production}
[[ -f "$envfile" ]] || { echo "Create $envfile securely first"; exit 1; }
compose=(docker compose --env-file "$envfile" -f compose.prod.yaml)
command=${1:-status}
case "$command" in
 login)
  : "${GHCR_USER:?}" "${GHCR_TOKEN:?}"
  printf '%s' "$GHCR_TOKEN" | docker login ghcr.io -u "$GHCR_USER" --password-stdin
  ;;
 deploy)
  : "${IMAGE_TAG:?Supply exact tested git SHA}"
  [[ "$IMAGE_TAG" =~ ^[a-f0-9]{40}$ ]] || { echo "Deploy requires a full git SHA"; exit 1; }
  "${compose[@]}" config --quiet
  "${compose[@]}" pull
  if [[ -f .deployed-tag ]]; then cp .deployed-tag .previous-tag; fi
  "${compose[@]}" up -d --wait --wait-timeout 300
  printf '%s\n' "$IMAGE_TAG" > .deployed-tag
  ;;
 rollback)
  [[ -f .previous-tag ]] || { echo "No recorded previous deployment"; exit 1; }
  export IMAGE_TAG
  IMAGE_TAG=$(<.previous-tag)
  [[ "$IMAGE_TAG" =~ ^[a-f0-9]{40}$ ]] || exit 1
  "${compose[@]}" pull
  "${compose[@]}" up -d --wait --wait-timeout 300
  printf '%s\n' "$IMAGE_TAG" > .deployed-tag
  ;;
 restart) "${compose[@]}" restart; "${compose[@]}" up -d --wait --wait-timeout 300 ;;
 stop) "${compose[@]}" stop ;;
 status|health) "${compose[@]}" ps; curl --fail --silent http://127.0.0.1/nginx-health ;;
 *) echo "Usage: $0 login|deploy|rollback|restart|stop|status|health"; exit 2 ;;
esac
# Never delete volumes. Schema rollback requires a separately tested backup/restore plan.
