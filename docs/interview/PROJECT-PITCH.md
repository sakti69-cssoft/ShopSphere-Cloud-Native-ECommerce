# ShopSphere Project Pitch

## Claim discipline

These pitches describe the existing AWS deployment and verified operations. Verified on 2026-09-07: CI run 34101039768 passed and published all eight scanned images at 5d3259f; those images were deployed through operator SSM. All fourteen containers were healthy, seven Prometheus targets were up and twelve rules were loaded. The GitHub OIDC deployment workflow still requires external IAM/Environment setup. Prometheus, Grafana and Node Exporter are deployed alongside eleven application/data containers. Five dashboards and twelve rules are provisioned; seven targets were verified up. A root-owned host timer collects selected Docker statistics into Node Exporter's read-only textfile mount. Privileged cAdvisor was removed and the host runtime socket paths are masked from Node Exporter. External notification delivery is not configured.

## 30-second version

ShopSphere is my cloud-native e-commerce portfolio project. It runs a Next.js storefront, Spring Cloud Gateway, five Java 21 microservices, and MySQL, MongoDB, and Redis in eleven Docker containers on AWS. Nginx provides trusted HTTPS, while Terraform manages the network, SSM-enabled EC2, encrypted EBS, security group, and Elastic IP. I focused on production safety: only ports 80 and 443 are public, prices are server-authoritative, checkout is idempotent and inventory-aware, database volumes persist, and Terraform plan review prevented an accidental EC2 replacement.

## 60-second version

ShopSphere is an end-to-end e-commerce and DevOps portfolio system. Customers can register, search the catalog, manage a persistent cart, checkout with stock protection, and view orders. The frontend is Next.js; API traffic flows through Nginx and Spring Cloud Gateway to separate Auth, Product, Inventory, Cart, and Order services built with Java 21 and Spring Boot. MySQL owns transactional users, stock, and orders; MongoDB owns the flexible catalog; Redis owns carts and rate limits.

It is deployed cost-consciously on one AWS `m7i-flex.large` EC2 with encrypted 40 GiB gp3 EBS and Docker Compose. DuckDNS points to an Elastic IP, Nginx terminates Let's Encrypt TLS, and only ports 80/443 are public; operations use SSM, not SSH. Terraform made the deployment reproducible, and its plan caught dynamic AMI drift before it could replace the instance. The repository also has CI/security/image/SBOM and approval-gated OIDC-to-SSM deployment workflows. Image publication and operator SSM deployment were verified after security fixes; the OIDC workflow remains configuration only.

## 2-minute technical version

ShopSphere solves a familiar marketplace workflow but is designed to demonstrate real engineering boundaries rather than a UI-only demo. The public request path is browser to DuckDNS, Elastic IP, AWS security group, and Nginx. Nginx handles TLS 1.2/1.3, HTTP redirect, security headers, frontend proxying, and API proxying. API requests go to Spring Cloud Gateway for routing, JWT and role checks, Redis-backed rate limits, correlation IDs, timeouts, circuit breakers, and safe GET retries.

Behind the Gateway are five Spring Boot services. Auth stores BCrypt credentials and profiles in MySQL. Product stores flexible catalog and review/wishlist documents in MongoDB. Inventory uses MySQL transactions and locking for available/reserved stock. Cart uses Redis AOF with a TTL. Order owns checkout, address snapshots, order/items, and durable idempotency in MySQL. The browser never sets authoritative prices: Order calls Product, calculates totals, reserves inventory, then persists. Identical completed retries return one order, while conflicting idempotency-key reuse returns 409. I also document the remaining distributed crash window because best-effort compensation is not a full Saga/outbox.

The production topology is deliberately one EC2 host because this is a cost-conscious portfolio deployment. Docker Compose runs eleven containers on a private bridge network, publishes only Nginx 80/443, drops application capabilities, sets health checks/restart policies, and preserves MySQL/MongoDB/Redis named volumes. AWS administration is through SSM; IMDSv2 is required and the root EBS is encrypted.

The operational story matters most. A Terraform plan revealed that a dynamic “latest” AMI would replace the running EC2 during an unrelated HTTPS change. I stopped, pinned the current reviewed AMI, preserved the state-compatible bootstrap SHA, and regenerated a no-replacement plan. HTTPS was then migrated in place with an EIP, ACME webroot, non-root Nginx, read-only key mount, and automatic renewal—without replacing EC2 or deleting data.

Verified on 2026-09-07: CI run 34101039768 passed and published all eight scanned images at 5d3259f; those images were deployed through operator SSM. All fourteen containers were healthy, seven Prometheus targets were up and twelve rules were loaded. The GitHub OIDC deployment workflow still requires external IAM/Environment setup.

## 5-minute deep-dive version

ShopSphere began as an e-commerce application, but I developed it into a platform and operations case study. The functional goal is a coherent customer journey: registration and login, catalog discovery, categories, search and autocomplete, product details, wishlist and reviews, persistent cart, addresses and coupons, checkout, order history, and inventory protection. Payment is simulated, which I state explicitly; the project is not presented as PCI-certified.

At the edge, a DuckDNS hostname resolves to a stable AWS Elastic IP. The security group exposes only TCP 80 and 443. Nginx redirects normal HTTP traffic, keeps the ACME HTTP-01 challenge available, terminates Let's Encrypt TLS 1.2/1.3, adds security headers, and proxies either to the Next.js frontend or Spring Cloud Gateway. The proxy adds forwarded headers and a correlation ID so a request can be followed across layers. Internal ports are visible only on the Docker network; SSH is closed publicly and I administer the instance through Systems Manager.

The application boundary is five Java 21/Spring Boot services plus Gateway. Auth owns identities and BCrypt hashes. Product owns product documents, filtering, pagination, search, reviews, and wishlist data. Inventory owns stock and reservations. Cart owns expiring user carts. Order owns checkout, calculated totals, item/address snapshots, status, history, and idempotency. MySQL fits relational constraints and transactions for users, stock, and orders. MongoDB fits flexible product attributes. Redis fits carts and rate-limit tokens. Each service owns its store instead of sharing tables directly.

Checkout demonstrates why those boundaries matter. The browser supplies product ID, quantity, shipping details, and an idempotency key—not the trusted price or total. Order resolves the active product and current price from Product, reserves stock through Inventory, calculates totals, and persists the order/items and idempotency result. A repeat with the same key and intent returns the original order; conflicting reuse is rejected. If a caught downstream failure occurs, Order attempts compensation. I do not overclaim this: a crash between distributed steps still needs an outbox/Saga/reconciliation mechanism in a commercial system.

The runtime is a single `m7i-flex.large` EC2 in `ap-south-1` with encrypted 40 GiB gp3 EBS. Docker Compose runs eight application/edge containers plus MySQL, MongoDB, and Redis. Application containers use non-root users where practical, drop capabilities, and disable privilege escalation. Health checks distinguish process start from readiness. Named volumes preserve database state across container recreation and host stop/start; deployments use `docker compose up -d --wait`, never `down -v`. This choice minimizes AWS cost and makes the topology understandable, but it is not HA: host and root EBS are shared failure domains.

Terraform defines the VPC, subnet, Internet Gateway, route, edge security group, IAM/SSM profile, EC2 settings, encrypted disk, and EIP. The strongest Terraform lesson came from an actual near miss. The AMI was selected with `most_recent`; when AWS published a newer Amazon Linux image, an unrelated plan proposed replacing EC2. Because user data also has replace-on-change semantics, applying casually could have destroyed the root volume. I stopped, verified the running AMI, replaced the dynamic lookup with a validated pinned AMI variable, passed the exact repository SHA dynamically, and reviewed a plan containing only the intended EIP/443 additions. That incident changed how I talk about IaC: reproducibility comes from deterministic inputs and plan review, not merely writing HCL.

The HTTPS migration was also intentionally incremental. I first made addressing stable, verified DNS and HTTP, then used a pinned Certbot container with Nginx's webroot so issuance did not require stopping the proxy. TLS runtime files stay off Git, the key is permissioned for the non-root Nginx process and mounted read-only, and a systemd timer performs renewal, validates config, and reloads Nginx. Public health and persistent records were rechecked, and EC2/EBS remained unchanged.

For delivery, GitHub Actions is divided into CI, image publication, and manual production deployment. CI validates scripts/Compose, builds and type-checks Next.js, runs Maven tests for Gateway plus five services, validates Terraform without applying it, and runs secret/IaC scans. Each of eight built images is supposed to pass a HIGH/CRITICAL Trivy gate and produce a CycloneDX SBOM before it reaches GHCR under a full Git SHA. A separate production workflow validates an exact commit on `main`, uses a protected Environment, is designed to assume a short-lived AWS role via OIDC, then invokes a defensive deploy script through SSM. That script records the previous immutable image revision, pulls before reconciliation, checks Compose/Nginx/public HTTPS, and restores the old application image/revision on failure without rolling database data backward.

Verified on 2026-09-07: CI run 34101039768 passed and published all eight scanned images at 5d3259f; those images were deployed through operator SSM. All fourteen containers were healthy, seven Prometheus targets were up and twelve rules were loaded. The GitHub OIDC deployment workflow still requires external IAM/Environment setup.

Prometheus, Grafana and Node Exporter are deployed alongside eleven application/data containers. Five dashboards and twelve rules are provisioned; seven targets were verified up. A root-owned host timer collects selected Docker statistics into Node Exporter's read-only textfile mount. Privileged cAdvisor was removed and the host runtime socket paths are masked from Node Exporter. External notification delivery is not configured.

The project taught me to value evidence and blast-radius control: trace an incident across layers, never fix persistence by deleting it, stop unsafe infrastructure plans, separate configured from verified, and state the trade-offs. My next commercial improvements would be multi-AZ stateless compute, managed data stores/private tiers, asymmetric identity and secret rotation, off-host encrypted backup/restore drills, external alerting, centralized logs/traces, and a transactional outbox/reconciliation path—only when requirements and budget justify them.

## Follow-up anchors

If the interviewer interrupts, pivot to one evidence-rich topic:

- **Terraform:** dynamic AMI drift and the stopped replacement plan.
- **Linux/Docker:** eleven-container recovery with exact named-volume verification.
- **Security:** SSM instead of SSH, only 80/443, IMDSv2, encrypted EBS, non-root edge.
- **Application integrity:** server-side pricing, stock reservation, and durable idempotency.
- **Delivery:** immutable SHA images and rollback without database rollback.
- **Honesty:** CI/CD and monitoring status boundaries.

End with a trade-off, not a technology list: “I chose a single-host design for cost and learning, proved its operational safety, and can explain the concrete migration path when availability requirements change.”
