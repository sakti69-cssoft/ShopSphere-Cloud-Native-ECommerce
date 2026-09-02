# Security and limitations
- BCrypt cost 12 for passwords. JWT HS256, issuer/expiry/type checks at gateway and cart/order; CUSTOMER/ADMIN authorization and ownership checks.
- Order resolves prices from Product, ignoring browser totals/discounts. Durable idempotency prevents duplicate completed checkout.
- Access tokens in sessionStorage; refresh tokens in localStorage. XSS can steal these. No token revocation/rotation store, MFA or external identity provider. Use HttpOnly cookie BFF/OIDC before real production.
- Shared signing secret is a portfolio simplification. Any holder can sign tokens; use asymmetric keys/JWKS and service identities later. First 32 UTF-8 bytes are used; provision at least 32 cryptographically random ASCII characters.
- Product/inventory trust the private service network and gateway for mutation authorization. They must not be exposed directly. Local ports bind loopback; production publishes only Nginx.
- Production application containers drop capabilities, forbid privilege escalation and run non-root. Databases use their upstream initialization privileges. Do not enable privileged mode or mount the Docker socket into applications.
- SQL schema users are separate. Mongo currently uses an administrator URI; replace with scoped app credentials. Redis is password protected, persistence enabled, with cart TTL.
- .env, keys, state, dependency/build folders and real tfvars are ignored. CI checks candidate/tracked paths and secret patterns; Trivy scans repository secrets/configuration without dependency resolution and scans built production images for vulnerabilities. Never print compose's expanded secrets.
- CI publishes only after test/security gates; GITHUB_TOKEN has package permissions only where needed. No AWS keys in code/workflows.
- Terraform requires IMDSv2, encrypted disk, explicit web SG and restricted optional SSH. No public DB ports. No WAF/TLS/HA/backups are claimed.
- Trivy exceptions document intended public web ingress and cost-conscious flow-log omission. Review before production, do not suppress all findings.

Threat boundaries: public Nginx -> authenticated gateway -> trusted internal network -> databases. A compromised internal service can reach other services/stores on the shared network. Split networks and mutual authentication before production. API rate limiting behind the proxy currently shares anonymous IP capacity; tune trusted proxy/IP handling before multi-user production.

Report issues privately to the repository owner once published; no invented security email is provided.
