#!/usr/bin/env bash
set -euo pipefail
umask 077
[[ ! -e .env.production ]] || { echo ".env.production already exists; refusing overwrite"; exit 1; }
: "${GHCR_OWNER:?Set GHCR_OWNER}" "${IMAGE_TAG:?Set immutable IMAGE_TAG}" "${FRONTEND_ORIGIN:?Set frontend origin}"
keys=(MYSQL_ROOT_PASSWORD AUTH_DB_NAME AUTH_DB_USERNAME AUTH_DB_PASSWORD INVENTORY_DB_NAME INVENTORY_DB_USERNAME INVENTORY_DB_PASSWORD ORDER_DB_NAME ORDER_DB_USERNAME ORDER_DB_PASSWORD MONGO_ROOT_USERNAME MONGO_ROOT_PASSWORD PRODUCT_DB_NAME REDIS_PASSWORD JWT_SECRET GHCR_OWNER IMAGE_TAG FRONTEND_ORIGIN)
for key in "${keys[@]}"; do
 value=${!key:-}
 [[ -n "$value" && "$value" != *replace* && "$value" != *$'\n'* && "$value" != *"'"* ]] || { echo "Missing/unsafe placeholder: $key"; exit 1; }
done
[[ ${#JWT_SECRET} -ge 32 ]] || { echo "JWT_SECRET must be at least 32 random characters"; exit 1; }
# Quote Compose values to prevent dollar interpolation; values are never logged.
for key in "${keys[@]}"; do printf "%s='%s'\n" "$key" "${!key}"; done > .env.production
printf "CART_TTL_HOURS=72\n" >> .env.production
echo "Created permission-restricted .env.production"
