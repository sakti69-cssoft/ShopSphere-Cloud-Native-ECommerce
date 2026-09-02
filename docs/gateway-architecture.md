# Gateway architecture

```text
Browser -> Nginx :80 -> Gateway :8080
                         |-> Auth :8081
                         |-> Product :8082
                         |-> Inventory :8083
                         |-> Cart :8084 -> Product :8082
                         `-> Order :8085 -> Inventory :8083
```

## Routes and versioning

| Edge route | Downstream route | Access |
| --- | --- | --- |
| `/api/v1/auth/**` | `/api/auth/**` | Login/register public; other endpoints authenticated |
| `/api/v1/products/**` | `/api/products/**` | GET public; mutations ADMIN |
| `/api/v1/inventory/**` | `/api/inventory/**` | Authenticated; stock updates ADMIN |
| `/api/v1/cart/**` | `/api/cart/**` | Authenticated |
| `/api/v1/orders/**` | `/api/orders/**` | Authenticated |

The gateway rewrites the preferred v1 paths to the unchanged Phase 3 controller paths. Unversioned `/api/<service>/**` routes remain temporarily available through the gateway for compatibility. Service-level security remains enabled where it already existed; gateway security is an additional boundary.

JWTs use the same HS256 secret as Auth. Malformed, expired, or missing tokens receive safe JSON 401 responses; insufficient roles receive 403. Authorization headers are forwarded and never logged. Production should replace the shared symmetric foundation with rotated asymmetric signing keys or an external identity provider.

## Traffic controls and resilience

CORS accepts the comma-separated `FRONTEND_ORIGIN` value, supports preflight, explicitly lists methods/headers, and never combines credentials with a wildcard origin. Redis token buckets use separate configurable login/register, read, and write rates. Rejected traffic receives 429.

The gateway uses a 2-second connect timeout, configurable 10-second response timeout, and Resilience4j circuit breakers. Product GET requests alone receive two short retries because reads are safe and idempotent. Cart mutations, inventory reservations, and order creation are never automatically retried. Product outages return a sanitized 503 fallback. Circuit breaker and gateway request metrics are exposed through Micrometer for future Prometheus integration.

Every request receives or preserves a validated `X-Correlation-ID`. The gateway logs method, path, status, latency, and correlation ID, and forwards the ID downstream. Tokens, cookies, bodies, passwords, and credentials are excluded.

## Service communication and consistency

Cart calls Product with `RestClient` to validate the product and obtain authoritative name, image, active state, and price. Order calls Inventory to reserve/release stock. Both clients use configurable URLs, connect/read timeouts, correlation propagation, and normalized failure handling. No service reads another service's database.

Order creation validates totals before reserving, reserves each item, then persists the confirmed order with simulated payment `PENDING`. If a later reservation or persistence operation fails, completed reservations are released on a best-effort basis. Compensation failures are preserved as suppressed errors. This is not a distributed transaction guarantee: a future event-driven Saga/outbox design should add durable commands, idempotency keys, and reconciliation.

Individual OpenAPI UIs remain available only on direct local debug ports at `http://localhost:8081-8085/swagger-ui.html`. The gateway listens on 8080 inside the Compose network and is optionally mapped to host port 18080 because this workstation already uses host port 8080; normal clients use Nginx on port 80. The gateway exposes only health, info, metrics, and Prometheus actuator endpoints; aggregation is deferred.
