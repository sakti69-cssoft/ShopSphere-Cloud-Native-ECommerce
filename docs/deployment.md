# Deployment runbook
No cloud deployment has been performed. These scripts prepare an operator-driven single-host demo. Do not expose real customer/payment data over HTTP.

## Local development
```powershell
Copy-Item .env.example .env
# Edit .env locally with strong random secrets; do not overwrite an existing .env.
docker compose --profile local config --quiet
docker compose --profile local up -d --build --wait
cd frontend
npm ci
npm run dev
```
Open http://localhost:3000. API entry: http://localhost/api/v1. Avoid displaying interpolated compose config in logs; use --quiet. Local service/database ports should be restricted to loopback.

## Later, on an explicitly provisioned AL2023 host
Copy this repository (or a reviewed release) without development secrets. Install Docker with sudo bash scripts/install-docker.sh. Use at least 8 GiB RAM and adequate disk. Set database names/users, random passwords, GHCR_OWNER, full IMAGE_TAG and FRONTEND_ORIGIN in a secure operator environment, then:
```sh
bash scripts/create-production-env.sh
# Only if packages are private; token must have read:packages:
bash scripts/deploy.sh login
IMAGE_TAG=FULL_TESTED_40_CHARACTER_GIT_SHA bash scripts/deploy.sh deploy
bash scripts/deploy.sh health
bash scripts/deploy.sh restart
bash scripts/deploy.sh rollback
bash scripts/deploy.sh stop
```
Production Compose publishes only Nginx port 80. HTTPS needs a separately configured certificate/domain and Nginx TLS listener; no self-signed or fabricated deployment is supplied. Do not enable public traffic with real credentials before TLS.

The env creation script refuses overwrite and uses mode 0600, validates required values and suppresses secret output. Mongo credentials in connection strings must be URI-safe (random hex is convenient). Existing database users/passwords are initialized only on first volume creation; changing .env does not rotate stored database credentials.

Deploy requires a full SHA and a green image matrix; there is no automatic deploy-on-push. Registry auth is optional for public GHCR packages. Never put a token in shell arguments; login uses stdin. Rollback switches image tags and keeps volumes; Flyway schema changes may not be backward-compatible, so test migrations/backups before rollback.

Take database-consistent MySQL/Mongo backups and Redis persistence backups to separate storage, and test restoring them. This repository does not claim automated backup or disaster-recovery certification. Never run docker compose down -v.

## Observability
```sh
export GRAFANA_ADMIN_PASSWORD='YOUR_RANDOM_LOCAL_SECRET'
docker compose -f compose.yaml -f compose.observability.yaml --profile local --profile observability up -d --wait
```
Grafana: localhost:3001; Prometheus: localhost:9090. Both bind loopback. On EC2 use an authenticated tunnel; do not open those ports publicly. The same override can be used with compose.prod.yaml and --env-file .env.production.
