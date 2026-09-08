# ShopSphere Docker Interview Q&A

## Image versus container

An image is the immutable filesystem/config template produced by a build. A container is a runtime instance with process state, networking, mounts, and writable layers. ShopSphere builds separate images for five business services, Gateway, frontend, and Nginx; production Compose runs those eight plus MySQL, MongoDB, and Redis—eleven long-running containers.

## What does a Dockerfile do?

It defines reproducible image layers, build inputs, runtime command, user, and metadata. ShopSphere uses multi-stage builds so Maven/Node toolchains stay out of runtime images. Java, Next.js, and Nginx runtime containers are intended to run non-root. Important base versions should be explicit and regularly scanned; a tag is not equivalent to an immutable digest.

## Why use Docker Compose?

Compose declares the single-host topology: images, environment references, dependencies, health checks, restart policies, private network, resource ceilings, published edge ports, and persistent volumes. It makes a coordinated eleven-container stack understandable and reproducible without the cost/complexity of Kubernetes.

## How does service discovery work?

Services join the named bridge network `shopsphere-prod` and resolve Compose service names such as `mysql`, `mongodb`, `redis`, `product-service`, and `gateway`. Internal applications listen on 8080–8085/3000/database ports but are not published on the host. Nginx is the only public entry and publishes 80/443.

## Volume versus bind mount

A named volume is Docker-managed persistent storage; ShopSphere uses `mysql-data`, `mongo-data`, and `redis-data`. A bind mount maps a host path, used for Nginx config and instance-local TLS/ACME runtime directories. Config/cert mounts are read-only inside Nginx. Deleting a container does not delete a named volume, but `docker compose down -v` does—so it is forbidden for routine operation.

## How do database volumes preserve data?

The production project name remains `shopsphere-prod`, so volume names remain stable. Compose reconciliation attaches the same named volumes after image/container recreation. MySQL/MongoDB/Redis use `restart: unless-stopped`, Redis enables AOF, and database init scripts run only for a new empty database as governed by upstream images. Persistence must be proved by checking mount names and representative records before/after a redeploy.

## What is a health check?

A health check is an application-aware probe separate from “the process exists.” Java services query Actuator health, frontend performs an internal HTTP fetch, databases use native ping commands, and Nginx checks `/nginx-health`. Health checks drive `depends_on` startup conditions and `docker compose up --wait`. They can still be shallow; public and business-data checks remain necessary.

## `depends_on` versus readiness

Start order alone is not readiness. ShopSphere uses `condition: service_healthy` so dependent services wait for database/service probes. This improves startup but does not replace retries, timeouts, circuit breakers, or recovery when a dependency fails later.

## What does `restart: unless-stopped` mean?

Docker restarts the container after daemon/host restart or process failure unless an operator deliberately stopped it. It improves recovery but can create a noisy crash loop and does not fix configuration or data problems. The 2026 production audit verified the restart policy and all eleven containers healthy after host lifecycle operations.

## How are containers hardened?

The common application definition sets `no-new-privileges` and drops all Linux capabilities. Runtime application images use non-root where practical. Service/database ports stay private, secrets arrive through the instance-local environment, and no application mounts the Docker socket. Health checks and resource ceilings limit some failure modes. Database upstream images retain the privileges they require, and privileged cAdvisor was replaced with host-collected textfile metrics.

## How does non-root Nginx work on ports 80/443?

Docker publishes host ports to container ports; the container process is user `nginx`. Its config places the PID under `/tmp`, logs to stdout/stderr, and uses writable cache/temp directories prepared with the correct ownership in the image. TLS files are mounted read-only with the private key readable only by the Nginx UID/group. This avoids running the proxy process as root while keeping host exposure at 80/443.

## How are secrets handled?

Tracked Compose files contain variable references, not production values. `.env.production` is generated/stored only on the host with restrictive permissions and is preserved during deploys. Runtime TLS and deploy-state paths are ignored. Avoid `docker inspect` without filters or `docker compose config` without `--quiet`, because both can reveal expanded environment values.

## What is the safe deployment command?

From `/opt/shopsphere`:

```bash
docker compose --env-file .env.production -f compose.prod.yaml up -d --wait --wait-timeout 300
```

It reconciles services and waits for health without deleting volumes. Pull/build only the reviewed application services when practical. Do not restart databases for a frontend-only change.

## How do immutable image tags help?

The CI design tags images with the full Git SHA, so the artifact maps back to reviewed source and rollback can select an exact prior build. `latest` may exist for convenience but should not define production. ShopSphere's proposed deploy script derives and records the currently running Nginx image owner/SHA before a change.

## What happens if an image pull fails?

The configured deployment script pulls all application images before container reconciliation. If a pull fails, it restores the checkout and leaves the running stack intact. If health fails after reconciliation, it restores the prior image tag and Git revision. This rollback is configured but its GitHub OIDC/SSM execution is not yet verified.

## How do you troubleshoot an unhealthy container?

Use Compose `ps`, bounded service logs, filtered inspect output, resource stats, dependency health, and an internal network request. Look for exit/OOM code, wrong image, missing mount, permission, DNS, connection refusal, migration, or health-probe mistakes. Make one reversible change and revalidate the public path and data.

## Why not publish every port for convenience?

Published ports bypass the intended Nginx/Gateway boundary and enlarge attack surface. Docker networking already provides internal reachability. For host troubleshooting, use `docker exec`, SSM, or temporary loopback-only bindings—never Internet ingress for MySQL, MongoDB, Redis, 3000, 8080–8085, 9090, or 9100.

## What is the monitoring-container caveat?

Prometheus, Grafana and Node Exporter are deployed alongside eleven application/data containers. Five dashboards and twelve rules are provisioned; seven targets were verified up. A root-owned host timer collects selected Docker statistics into Node Exporter's read-only textfile mount. Privileged cAdvisor was removed and the host runtime socket paths are masked from Node Exporter. External notification delivery is not configured.

## What are the limits of Compose here?

One host remains one failure and scaling domain; local volumes bind data to its EBS; rolling updates and rescheduling are limited; secrets are file/environment based; and service discovery has no multi-host control plane. Those are accepted portfolio constraints, not hidden defects.
