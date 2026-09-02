# ShopSphere backend architecture

## Service topology

```text
ShopSphere Next.js frontend
           |
           v
Future Nginx / API Gateway
           |
           +--> Auth Service      :8081  --> MySQL (Phase 3)
           +--> Product Service   :8082  --> MongoDB (Phase 3)
           +--> Inventory Service :8083  --> MySQL (Phase 3)
           +--> Cart Service      :8084  --> Redis (Phase 3)
           +--> Order Service     :8085  --> MySQL (Phase 3)
```

Each service is an independent Spring Boot application with its own Maven build, domain model, DTO validation, repository abstraction, service layer, controllers, error handling, Actuator health endpoint, OpenAPI UI, tests, configuration, and Dockerfile. There is intentionally no shared Java domain library, allowing service contracts and persistence choices to evolve independently.

## Responsibilities and interactions

- **Auth service** owns users, BCrypt password hashes, CUSTOMER/ADMIN roles, access tokens, refresh-token foundations, and profile lookup. JWTs use HS256 for Phase 2; the secret must be supplied through `JWT_SECRET` outside local development.
- **Product service** owns catalog data, search, filters, pagination, sorting, category/brand attributes, images, specifications, and rating summaries. Catalog reads are public; mutations require an ADMIN JWT.
- **Inventory service** owns available and reserved stock. Atomic synchronized operations prevent negative stock, over-reservation, and over-release. A transactional database implementation replaces the in-memory repository in Phase 3.
- **Cart service** owns per-user carts and totals. Its `ProductPricingGateway` ensures prices come from the product boundary instead of client payloads. A real HTTP client and Redis repository are Phase 3 work.
- **Order service** owns order creation, totals, address snapshots, simulated payment states, status transitions, and cancellation. Its `InventoryGateway` is the seam for reservation/release calls and compensation behavior.

## API conventions

All APIs use `/api/...`, Jakarta Bean Validation, JSON request/response bodies, and a consistent error envelope containing timestamp, HTTP status, reason, safe message, path, and field validation errors. Stack traces are not returned. Health is exposed only at `/actuator/health`; Swagger UI is available at `/swagger-ui.html` and OpenAPI JSON at `/v3/api-docs`.

## Persistence migration plan

Phase 2 uses independent in-memory repository implementations so all tests and packages run without infrastructure. Environment-variable placeholders document the target connections. Phase 3 will add database adapters behind the existing repository interfaces, migrations/indexes where appropriate, Redis TTL policies, resilient HTTP clients, timeouts, and distributed transaction/outbox decisions.

## Security boundaries

The auth service issues short-lived access tokens and refresh tokens and never serializes password hashes. Product mutations validate the ADMIN role. Future gateway integration should validate tokens centrally as an additional layer while services continue enforcing their own authorization. Secrets, database passwords, and tokens must remain outside source control.

## Container and gateway preparation

Every Dockerfile uses a Maven/Java 21 multi-stage build and a non-root Alpine JRE runtime. The future gateway should route service prefixes, propagate correlation IDs, apply rate limits, enforce request-size limits, and terminate TLS through Nginx or the selected managed gateway.
