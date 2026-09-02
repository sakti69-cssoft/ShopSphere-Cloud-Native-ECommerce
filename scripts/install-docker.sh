#!/usr/bin/env bash
set -euo pipefail
# Amazon Linux 2023; run as root. Docker group grants root-equivalent access.
[[ $EUID -eq 0 ]] || { echo "Run with sudo"; exit 1; }
dnf install -y docker curl
systemctl enable --now docker
install -d /usr/local/lib/docker/cli-plugins
version=v2.39.4
tmp=$(mktemp -d)
trap 'rm -f "$tmp/docker-compose-linux-x86_64" "$tmp/checksums.txt"; rmdir "$tmp"' EXIT
curl -fsSL "https://github.com/docker/compose/releases/download/$version/docker-compose-linux-x86_64" -o "$tmp/docker-compose-linux-x86_64"
curl -fsSL "https://github.com/docker/compose/releases/download/$version/checksums.txt" -o "$tmp/checksums.txt"
(cd "$tmp"; grep ' docker-compose-linux-x86_64$' checksums.txt | sha256sum -c -)
install -m 0755 "$tmp/docker-compose-linux-x86_64" /usr/local/lib/docker/cli-plugins/docker-compose
docker compose version
