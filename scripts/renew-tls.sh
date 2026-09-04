#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
domain=shopsphere-sakti.duckdns.org
mkdir -p runtime/certbot/www runtime/certbot/conf runtime/tls
docker run --rm -v "$PWD/runtime/certbot/www:/var/www/certbot" -v "$PWD/runtime/certbot/conf:/etc/letsencrypt" certbot/certbot:v5.4.0 renew --webroot -w /var/www/certbot --quiet
install -o 101 -g 101 -m 0644 "runtime/certbot/conf/live/$domain/fullchain.pem" runtime/tls/fullchain.pem
install -o 101 -g 101 -m 0640 "runtime/certbot/conf/live/$domain/privkey.pem" runtime/tls/privkey.pem
docker compose --env-file .env.production -f compose.prod.yaml exec -T nginx nginx -t
docker compose --env-file .env.production -f compose.prod.yaml exec -T nginx nginx -s reload
