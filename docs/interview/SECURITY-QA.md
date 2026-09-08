# ShopSphere Security Interview Q&A

## What is the security model?

ShopSphere uses layered controls: AWS admits only web traffic; Nginx terminates HTTPS and adds edge headers; Gateway applies API routing, JWT/role policy, rate limits, timeouts, and correlation IDs; sensitive services validate authorization/ownership; server-side pricing and inventory rules protect business integrity; containers reduce privilege; secrets/data stay outside Git; CI scans source/config/images. It is a portfolio security baseline, not a compliance certification.

## How is HTTPS implemented?

DuckDNS resolves `shopsphere-sakti.duckdns.org` to EIP `65.2.225.30`. Nginx listens on 443 with the full chain/key, TLS 1.2 and 1.3, disabled session tickets, HSTS, and browser security headers. Port 80 serves only ACME HTTP-01 content and redirects normal paths to HTTPS. A pinned Certbot container renews via webroot; a systemd timer validates and reloads Nginx. Certificate/key runtime material is instance-local, Git-ignored, and mounted read-only.

## Why leave port 80 open?

The selected Let's Encrypt HTTP-01 renewal path needs it. Nginx restricts the challenge location and redirects all other requests. DNS-01 could remove that requirement but would need safe DNS API credentials and automation.

## Which ports are public?

Only TCP 80 and 443. SSH 22, frontend 3000, Gateway/services 8080–8085, MySQL, MongoDB, Redis, Prometheus, Grafana, Node Exporter, Docker API, and other internal ports are not public. SSM replaces public SSH.

## How are passwords protected?

Auth uses BCrypt with cost 12 rather than reversible encryption. Production database/JWT/Grafana values are external environment secrets on the instance, not tracked examples. Password hashes are not returned. The project does not claim MFA, a managed secrets service, or full password-compromise controls.

## How do JWTs work here?

Auth issues signed access/refresh tokens with issuer, expiry, and token-type checks. Gateway and owning services validate access tokens and roles/ownership. The current HS256 shared secret simplifies the portfolio topology but expands blast radius; a stronger design would use asymmetric signing/JWKS, refresh rotation/revocation, and HttpOnly/BFF browser storage.

## What is defense in depth for authorization?

Gateway rejection reduces unnecessary internal traffic, but service-level ownership/role checks protect sensitive resources even if a route is misconfigured. The public catalog incident demonstrates why route matchers must be explicit: GET collection/item reads are permitted, mutations remain admin-only.

## How is pricing tampering prevented?

The client sends product identity and quantity, not authoritative money. Cart/Order resolve the current product price from Product Service and calculate totals server-side. Order persists item snapshots, claims a unique idempotency key/intent, and coordinates inventory reservation. This protects integrity even if browser JavaScript is modified.

## How are duplicate orders prevented?

The Order Service persists an idempotency record with a unique key and intent hash. A completed identical retry returns the existing order; conflicting key reuse returns 409. This covers normal retries but not every distributed crash window; without a transactional outbox/reconciliation worker, best-effort compensation remains a documented limitation.

## How are containers hardened?

Application/proxy runtimes use non-root where practical, `no-new-privileges`, dropped capabilities, health checks, private networking, read-only config/certificate mounts, and no Docker socket. Databases retain upstream-required privilege. Resource limits reduce some denial-of-service blast radius but must be validated under load.

## How is AWS hardened?

IMDSv2 is required, EBS is encrypted, the edge SG exposes only 80/443, SSM replaces public SSH, and EC2 receives an instance profile rather than hardcoded AWS keys. This is not a full landing zone: centralized audit/logging, multi-account controls, managed secrets, WAF, private tiers, and off-host backup controls remain roadmap items.

## How are secrets kept out of Git?

`.gitignore` excludes `.env*` except templates, runtime TLS, Terraform state/plan/tfvars, keys, deploy state, and backup artifacts as applicable. `scripts/check-secrets.mjs` examines tracked and non-ignored candidates for forbidden paths and common private-key/AWS/GitHub-token patterns without reading ignored environment files. Trivy performs a separate repository secret scan. Scanners reduce risk but do not prove that no secret exists; history and rotation procedures still matter.

## What security scans are configured?

- Custom secret/path hygiene.
- Trivy repository secret scanning.
- Trivy IaC/configuration HIGH/CRITICAL gate with reviewed suppressions.
- Per-image HIGH/CRITICAL vulnerability scan before publication.
- CycloneDX SBOM generation.
- Dependabot coverage for npm, Maven, actions, Docker, and Terraform.

The image gate initially blocked publication. After runtime package/Tomcat/npm fixes, all eight images passed their fixable HIGH/CRITICAL gates and were published and deployed through operator SSM. Do not claim Gitleaks, SAST, signing, npm audit, or Maven OWASP results unless Phase 8 adds and verifies them.

## How should vulnerability findings be handled?

Confirm the affected artifact/version/path and exploitability, distinguish application from base/OS/transitive packages, remediate clearly exploitable HIGH/CRITICAL issues first, rebuild and rescan, and document residual risk with an owner/expiry. Do not blindly major-upgrade dependencies or create a broad ignore file merely to obtain green CI.

## What is least privilege for the future deploy workflow?

OIDC trust should constrain repository, branch/environment, audience, and account. The role should target only the existing instance and permitted SSM document/actions plus command-result reads. It should not grant EC2 creation/termination, IAM administration, Terraform apply, secrets listing, or wildcard infrastructure control. This external role is not yet verified.

## What headers does Nginx add?

HSTS, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, strict-origin referrer policy, and a permissions policy disabling camera/microphone/geolocation. `server_tokens off` reduces passive version disclosure. A Content Security Policy is a worthwhile next control but must be tested against Next.js assets rather than added blindly.

## Is CORS an authentication control?

No. CORS controls which browser origins may read responses; it does not stop non-browser clients. Authentication, authorization, CSRF design, input validation, and ownership rules remain required. ShopSphere uses a configured frontend origin through Gateway and same-origin production routing.

## What are the most important known risks?

- Single host and shared root EBS failure domain.
- HS256 shared JWT secret and browser-local refresh token.
- MongoDB application connection uses an initialization administrator in the demo stack.
- Some services trust the shared internal Docker network.
- Simulated payment and no PCI design.
- No WAF, managed secrets, centralized security logging, or verified off-host restore yet.
- GitHub OIDC workflow execution remains unverified; operator SSM image deployment and monitoring were verified.
- The host telemetry timer retains Docker administration access; its code and units must remain root-owned. cAdvisor and its socket exposure were removed.

## How would you respond to a suspected secret leak?

Stop dissemination, identify affected secret/scope/history without printing it, revoke/rotate at the authority, update dependent runtime securely, verify service recovery, scan Git history/logs/artifacts, invalidate sessions if relevant, and document timeline/root cause/prevention. Deleting a file from the latest commit is insufficient once a secret has been exposed.

## What would you add for a commercial deployment?

Managed secret rotation, asymmetric identity/MFA, private subnets and managed data stores, WAF/rate abuse controls, centralized immutable audit/security logs, image signing/provenance, dependency/SAST policy, external alerting, off-host encrypted backups with access separation, and formal threat modeling/SLO/incident processes.
