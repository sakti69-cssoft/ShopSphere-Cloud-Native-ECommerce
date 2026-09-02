# Interview guide
| Question | Concise answer |
|---|---|
| Why microservices? | Demonstrates independent ownership/runtime/store boundaries; adds distributed consistency and operational cost. A small shop could use a modular monolith. |
| Why Gateway? | Central JWT/role enforcement, rate limits, routing and resilience. |
| Why Nginx as well? | Public reverse proxy/static web edge; gateway handles application policies. |
| Why three stores? | MySQL for transactional auth/stock/orders, Mongo for flexible catalog, Redis for expiring cart/rate counters. |
| Why Terraform? | Reviewed reproducible infrastructure graph, state and change plans. |
| Plan vs apply? | Plan previews changes; apply mutates billable infrastructure. Validation does neither. |
| Idempotency? | Durable request key/intent maps repeat checkout to one order. Ambiguous crashes still require reconciliation. |
| Circuit breaker? | Stops repeated calls to a failing dependency; timeout bounds waiting, breaker enables recovery. |
| Rate limiting? | Protects scarce backend capacity and authentication; 429 is a controlled rejection. |
| CI vs CD? | CI proves source through tests/scans; delivery publishes artifacts; deployment is an explicit operator action here. |
| Registry? | GHCR stores versioned images; deploy immutable SHA, not mutable latest. |
| Prometheus vs Grafana? | Prometheus collects/queries metrics and evaluates rules; Grafana visualizes them. |
| Security groups? | Stateful network allow rules; only edge web and optional single-IP SSH, not DB ports. |
| Reverse proxy? | Receives public requests and routes to private upstream services. |
| Horizontal scaling? | Stateless app replicas are possible; shared DB/idempotency/Redis coordinate state. Single-host Compose is not HA. |
| Pricing security? | Client sends identity/quantity, server resolves price and totals; tampered money fields are ignored. |
| Limitations? | Simulated payment, shared JWT secret/local refresh tokens, best-effort compensation, no TLS/HA/managed backups. |
| What was deployed to AWS? | Nothing. Terraform and scripts are prepared; no cloud resource creation is claimed. |
