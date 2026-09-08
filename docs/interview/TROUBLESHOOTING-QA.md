# ShopSphere Troubleshooting Q&A

## How do you troubleshoot without making the outage worse?

I first freeze unrelated change, define the symptom and scope, and collect bounded evidence. I check the public endpoint, AWS status/SSM, container health, resource pressure, and dependency connectivity in that order. I change one thing at a time, prefer a reversible service-level action, preserve named volumes, and validate the customer path plus persistence afterward. I never use `docker compose down -v`, database reinitialization, an EC2 replacement, or an open SSH rule as a troubleshooting shortcut.

## A container is restarting. What do you do?

1. Run `docker compose ... ps` and inspect its health/restart count.
2. Read `docker compose ... logs --tail 200 SERVICE`; do not dump all logs or secrets.
3. Inspect health, image, mounts, restart policy, exit code, and OOM state with filtered `docker inspect` output.
4. Check `docker stats --no-stream`, `free -h`, `df -h`, and Docker daemon logs.
5. Test only the failing dependency from the service network.
6. Correct configuration/code or restart only that service, then wait for health and retest public routes.

A restart loop is a symptom. Restarting repeatedly without identifying an exit code, health failure, dependency error, or resource limit destroys useful evidence.

## The API returns HTTP 500. What evidence matters?

I capture the timestamp, route, correlation ID, status, and a sanitized reproduction. I follow the request from Nginx to Gateway to the owning service, then check the service's database/dependency. A 500 means the request reached an application boundary but was not safely handled; I distinguish code exceptions from database connection errors, invalid state, and upstream timeouts. I verify that a failed write did not create a partial order or consume inventory before retrying.

## Nginx returns 502. What does it usually mean?

Nginx could not obtain a valid response from its configured upstream. I run `nginx -t`, inspect the Nginx error log, verify frontend/Gateway container health, confirm both share `shopsphere-prod`, and call the upstream by Docker DNS from inside the Nginx network. I also check whether an image rollout changed the service name/port. I do not expose the upstream port publicly to diagnose it.

## The database is unavailable. What do you check?

- Database container health, exit/OOM state, disk space, and bounded logs.
- Named-volume mount identity; a new Compose project name can silently create a different empty volume.
- Docker DNS and network membership.
- Whether credentials were changed after the persistent database initialized. Environment changes do not recreate existing database users.
- Flyway status and whether the application uses the expected service-owned schema/user.

I never delete the volume to “fix” authentication. I rotate credentials with a controlled database operation or, once Phase 7 has produced and tested one, restore from a verified backup.

## How do you diagnose disk pressure?

Use `df -h /` to confirm the filesystem, then bounded `du` on `/var/lib/docker`, `/opt/shopsphere`, logs, monitoring retention, and backup directories. Check inode usage if size looks normal. Remove only identified, reproducible artifacts under a reviewed retention policy. Never blindly prune volumes; ShopSphere's named volumes contain business data.

## How do you diagnose high CPU or memory?

Correlate `top -b -n1`, `ps`, `docker stats --no-stream`, container limits, JVM heap/GC metrics, request/error rates, and recent deployments. High CPU can be legitimate load, GC churn, a loop, or database work. Low `free` memory alone is not proof of trouble because Linux uses cache; `available`, swap/OOM events, latency, and container limit proximity matter. Apply a bounded fix, not a dangerous sustained load test on production.

## A deployment health check fails. What happens?

The source-configured deploy script records the previous Git/image revision, validates the candidate SHA, pulls images before reconciliation, runs Compose with `--wait`, validates Nginx, and checks three public HTTPS endpoints. On failure it restores the previous checkout and immutable image owner/tag and reconciles the application stack. Named volumes and database data are not rolled back. This mechanism is configured and locally validated; the GitHub OIDC/SSM workflow has not yet been proven by a successful production run.

## Real incident STAR answers

### 1. Terraform AMI drift nearly replaced production EC2

**Situation:** Terraform selected the latest Amazon Linux 2023 AMI dynamically. After AWS published a newer image, refresh resolved a different AMI from the one running on `i-0e2cf0109097385b1`.

**Task:** Add the EIP and HTTPS ingress without replacing the instance or its root volume.

**Action:** I treated the plan as a change-control gate and stopped because `aws_instance` replacement was unrelated to HTTPS. I verified the running AMI `ami-090d68841c2a28756`, replaced the `most_recent` lookup with a validated `ami_id` variable pinned to that reviewed AMI, preserved the state-compatible `repository_ref`, then regenerated the plan. Only EIP and TCP 443 additions remained.

**Result:** The existing EC2 and EBS were preserved, HTTPS networking was applied separately, and commit `f24c64e` documents the deterministic AMI design. The lesson is that “latest” is convenient at build time but unsafe as an implicit replacement trigger in a stateful environment.

### 2. MySQL initialization failed during bootstrap

**Situation:** The official MySQL entrypoint sourced ShopSphere's database initialization script. The script enabled `set -u`, which leaked nounset behavior into the parent entrypoint and broke its optional variables.

**Task:** Make initialization reliable without discarding a database volume.

**Action:** I traced the failure to shell-option inheritance rather than credentials or MySQL itself. The script retained fail-fast `set -e` but removed nounset, with a comment explaining why sourced entrypoint scripts are special. I rebuilt/reconciled only what was required.

**Result:** Initialization completed without a volume reset. Commit `183f2d0` records the fix. The interview lesson is to understand process/sourcing semantics before reaching for destructive cleanup.

### 3. Public product API returned 401

**Situation:** Anonymous catalog listing was intended to be public, but the exact collection route returned 401 while nested product routes matched the permit rule.

**Task:** Restore public catalog reads without opening product mutations.

**Action:** I compared the request path with Spring Security matchers and found that `/api/products/**` did not cover the exact `/api/products` path in this configuration. I explicitly permitted both GET `/api/products` and GET `/api/products/**`; all other actions still require the appropriate role.

**Result:** Public listing worked while admin mutations remained protected. Commit `da3b5d4` captures the narrow route matcher correction.

### 4. Search and autocomplete did not behave like the UI promised

**Situation:** The initial search flow used mismatched sort parameters, limited query behavior, and no real autocomplete, so relevant catalog results were missed or misleading.

**Task:** Make product discovery work through the public storefront and preserve backend filtering rules.

**Action:** I aligned frontend and backend sort names, passed optional filters through the search endpoint, trimmed input, split multi-word queries, matched every term across name/description/brand/category, added paginated total counts, and implemented debounced autocomplete. I added a unit test for case-insensitive, partial, multi-word, and category matches.

**Result:** Search behavior became consistent across in-memory tests and MongoDB production behavior. Commit `84cb137` contains the implementation and regression test.

### 5. Public connectivity became unreliable after EC2 stop/start

**Situation:** A raw ephemeral EC2 IPv4 address changed across stop/start and users also attempted HTTPS before port 443/certificates were ready. The app could be healthy internally while external phones timed out or used a stale address.

**Task:** Separate application health from network/DNS/TLS causes and provide a stable shareable endpoint.

**Action:** I verified EC2 system/instance checks, SSM, Docker health, Nginx port 80, security-group ingress, and external HTTP. I then associated an Elastic IP, mapped `shopsphere-sakti.duckdns.org`, and tested DNS and HTTP before enabling trusted TLS.

**Result:** The stable public path is `https://shopsphere-sakti.duckdns.org`; only 80/443 are public. The lesson is to troubleshoot layer by layer instead of changing application code for an addressing problem.

### 6. Safe HTTPS migration on a live single host

**Situation:** ShopSphere was serving HTTP on the existing EC2 with persistent customer data; replacing the host or stopping Nginx would add avoidable risk.

**Task:** Add browser-trusted HTTPS and renewal in place.

**Action:** I first added stable EIP/443 infrastructure through a reviewed no-destroy plan. On the host, I used Certbot's pinned container and HTTP-01 webroot so Nginx stayed available. Certificate runtime paths are Git-ignored, mounted read-only, and copied with permissions suitable for the non-root Nginx UID. I validated `nginx -t`, enabled TLS 1.2/1.3 and security headers, preserved the ACME path on HTTP, redirected other HTTP traffic, and installed a persistent systemd renewal timer that validates and reloads Nginx.

**Result:** Public HTTPS became trusted and the existing EC2, EBS, volumes, and database data remained unchanged. Commit `605a5e8` contains the reproducible configuration.

### 7. Proving persistence instead of assuming it

**Situation:** Application rebuilds, Compose reconciliation, and host stop/start could appear successful while silently selecting new empty volumes.

**Task:** Prove that products, accounts, orders, inventory, and cart/store data survived non-destructive operations.

**Action:** I recorded representative product, user, inventory, and order counts, inspected exact named-volume mounts, used `docker compose up -d --wait` without volume deletion, and rechecked public paths and records afterward. Database services use `unless-stopped`, and the runbook checks the expected `shopsphere-prod_*` volumes.

**Result:** The persistence checks passed in the recorded production audits, including the 41-product catalog snapshot. The key lesson is that “container is healthy” is not equivalent to “the correct persistent data is mounted.”

### 8. Closing the deployment/rollback gap

**Situation:** Production had been updated through controlled SSM operations, but the repository did not yet encode one repeatable, approval-gated rollback path. Partial image changes or a failed health check were operational risks.

**Task:** Design delivery without public SSH, mutable tags, or database-destructive rollback.

**Action:** I created a manual GitHub workflow requiring an exact SHA on `origin/main`, OIDC credentials, the fixed instance ID, and SSM. The host script refuses dirty state, records the previous immutable image revision, pulls before reconciliation, verifies Compose/Nginx/public health, and restores the prior application revision on failure. It never runs Terraform or removes volumes.

**Result:** Commit `85056b8` contains the configuration and local validation. Later evidence: all eight image gates passed after Phase 8 fixes and operator SSM deployment succeeded. The isolated Phase 9 health-gate rollback demonstration passed; the automated GitHub OIDC workflow remains unverified.

## What makes a strong incident answer?

Name the customer symptom, cite concrete evidence, explain why the first hypothesis could be wrong, show the smallest safe fix, validate at every affected boundary, and state what remained unverified. Destructive “fixes” are not evidence of troubleshooting skill.
