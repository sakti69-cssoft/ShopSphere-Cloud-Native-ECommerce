#!/usr/bin/env bash
set -Eeuo pipefail

repo_dir=${SHOPSPHERE_DIR:-$(cd "$(dirname "$0")/.." && pwd)}
cd "$repo_dir"

target_revision=${1:?Usage: deploy-production-revision.sh REVISION GHCR_OWNER [PUBLIC_URL]}
target_owner=${2:?Usage: deploy-production-revision.sh REVISION GHCR_OWNER [PUBLIC_URL]}
public_url=${3:-https://shopsphere-sakti.duckdns.org}
env_file=${ENV_FILE:-.env.production}

[[ "$target_revision" =~ ^[0-9a-f]{40}$ ]] || {
  echo "Revision must be an exact lowercase 40-character Git SHA" >&2
  exit 2
}
[[ "$target_owner" =~ ^[a-z0-9][a-z0-9-]*$ ]] || {
  echo "GHCR owner contains unexpected characters" >&2
  exit 2
}
[[ "$public_url" == https://shopsphere-sakti.duckdns.org ]] || {
  echo "Unexpected production URL" >&2
  exit 2
}
[[ -f "$env_file" ]] || {
  echo "Production environment file is missing" >&2
  exit 2
}

if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "Tracked production checkout has local changes; refusing deployment" >&2
  exit 2
fi

git fetch --quiet --no-tags origin main
git cat-file -e "${target_revision}^{commit}"
git merge-base --is-ancestor "$target_revision" origin/main || {
  echo "Requested revision is not on origin/main" >&2
  exit 2
}

previous_revision=$(git rev-parse HEAD)
current_nginx_image=$(docker inspect --format '{{.Config.Image}}' shopsphere-prod-nginx-1)
if [[ "$current_nginx_image" =~ ^ghcr\.io/([^/]+)/shopsphere-nginx:([0-9a-f]{40})$ ]]; then
  previous_owner=${BASH_REMATCH[1]}
  previous_tag=${BASH_REMATCH[2]}
else
  echo "Cannot derive the running immutable image revision; refusing deployment" >&2
  exit 2
fi

compose=(docker compose --env-file "$env_file" -f compose.prod.yaml)
application_services=(
  auth-service product-service inventory-service cart-service
  order-service gateway frontend nginx
)

verify_release() {
  "${compose[@]}" ps
  "${compose[@]}" exec -T nginx nginx -t
  curl --fail --silent --show-error --output /dev/null "${public_url}/"
  curl --fail --silent --show-error --output /dev/null "${public_url}/nginx-health"
  curl --fail --silent --show-error --output /dev/null "${public_url}/api/v1/products"
}

rollback_release() {
  echo "Deployment failed; restoring application revision ${previous_revision}"
  git checkout --quiet --detach "$previous_revision"
  export GHCR_OWNER=$previous_owner
  export IMAGE_TAG=$previous_tag
  "${compose[@]}" config --quiet
  "${compose[@]}" up -d --wait --wait-timeout 300
  verify_release
  echo "Previous application revision restored"
}

mkdir -p .deploy-state
chmod 700 .deploy-state
printf '%s\n' "$previous_revision" > .deploy-state/previous-revision
printf '%s\n' "$previous_owner" > .deploy-state/previous-owner
printf '%s\n' "$previous_tag" > .deploy-state/previous-tag
chmod 600 .deploy-state/*

git checkout --quiet --detach "$target_revision"
export GHCR_OWNER=$target_owner
export IMAGE_TAG=$target_revision

if ! "${compose[@]}" config --quiet; then
  rollback_release
  exit 1
fi

if ! "${compose[@]}" pull "${application_services[@]}"; then
  echo "Image pull failed before container reconciliation; restoring checkout"
  git checkout --quiet --detach "$previous_revision"
  exit 1
fi

if ! "${compose[@]}" up -d --wait --wait-timeout 300 || ! verify_release; then
  rollback_release
  exit 1
fi

printf '%s\n' "$target_revision" > .deploy-state/deployed-revision
printf '%s\n' "$target_owner" > .deploy-state/deployed-owner
chmod 600 .deploy-state/deployed-*
echo "Deployment healthy at revision ${target_revision}"
