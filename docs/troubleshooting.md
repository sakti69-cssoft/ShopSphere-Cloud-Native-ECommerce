# Troubleshooting
| Symptom | Check |
|---|---|
| Docker pipe missing | Start healthy Docker Desktop, run docker info. Do not reset volumes. |
| Maven/Docker registry DNS failure | Check host and container DNS/proxy; retry downloads. Host packaging + compose.build-local.yaml is an explicit offline fallback, not a claim that CI multi-stage builds passed. |
| npm EPERM cache | Close conflicting processes or grant normal user access to the cache; do not delete unrelated files. |
| Service unhealthy | docker compose --profile local ps; inspect that service's bounded logs without publishing secrets. |
| MySQL access denied after env edit | Existing volume retains original users/passwords; rotate with SQL, not volume deletion. |
| 401 | Access token missing/expired/invalid type; login again. |
| 403 admin | Customer cannot mutate admin endpoints; frontend guard is not the security boundary. |
| 409 checkout | Reuse the original key for an identical retry; conflicting/in-progress requests must be reconciled. |
| 503 products | Check Product health and circuit breaker recovery interval. |
| 429 | Wait for rate-limit refill; do not add retries to checkout writes. |
| Broken/unsupported image URL | Local category fallback; configure only audited HTTPS image hosts. |
| Empty Grafana latency | Generate requests and wait at least two scrape intervals. |
| Terraform init fails | Registry access is required even with backend disabled; validation is not apply. |
| GHCR pull denied | Owner lowercase, full SHA available, packages visibility or read:packages login. |

Inspect only relevant logs. Do not post .env or token-bearing request headers. Application restart: docker compose --profile local restart auth-service product-service inventory-service cart-service order-service gateway nginx. Named volumes remain.
