# ShopSphere Production Operations Runbook

This runbook operates the existing single-host ShopSphere portfolio deployment. It is deliberately conservative: preserve the EC2 instance, encrypted EBS root disk, Docker named volumes, production environment file, and TLS runtime directory. Use AWS Systems Manager (SSM), not public SSH.

## Production baseline

| Item | Current value |
|---|---|
| Public URL | `https://shopsphere-sakti.duckdns.org` |
| Region / Availability Zone | `ap-south-1` / `ap-south-1a` |
| EC2 | `i-0e2cf0109097385b1`, `m7i-flex.large`, Amazon Linux |
| Public address | Elastic IP `65.2.225.30` |
| Root storage | `vol-0ed9fa47e65c3fd84`, encrypted 40 GiB gp3 |
| Metadata | IMDSv2 required |
| Administration | SSM Online; instance role uses `AmazonSSMManagedInstanceCore` |
| Public ingress | TCP 80 and TCP 443 only |
| Compose directory | `/opt/shopsphere` |
| Compose project | `shopsphere-prod` |
| Compose inputs | `.env.production` and `compose.prod.yaml` |
| Persistent volumes | `shopsphere-prod_mysql-data`, `shopsphere-prod_mongo-data`, `shopsphere-prod_redis-data` |
| TLS renewal | `shopsphere-certbot-renew.timer` |

The host SSH daemon may listen on TCP 22, but the AWS security group does not admit it. A host listener is not the same as Internet exposure. Do not add an SSH ingress rule.

## Non-negotiable safety rules

- Never run `terraform destroy`, terminate or replace the EC2 instance, delete the EBS volume, or delete Docker named volumes during ordinary operations.
- Never run `docker compose down -v`.
- Do not display `.env.production`, expanded Compose configuration, database passwords, JWTs, AWS credentials, or certificate private keys.
- Keep MySQL, MongoDB, and Redis data during deploys. Use `docker compose up -d --wait`, not a volume-destructive teardown.
- Keep internal service and database ports private. Only Nginx publishes 80/443.
- Take a backup and assess migration compatibility before deploying schema changes or rolling back application binaries.

## Access and daily health check

Start an SSM shell from an authenticated operator workstation:

```bash
aws ssm start-session --target i-0e2cf0109097385b1 --profile shopsphere --region ap-south-1
sudo -iu ec2-user
cd /opt/shopsphere
```

Then perform the non-secret daily check:

```bash
systemctl is-active docker amazon-ssm-agent shopsphere-certbot-renew.timer
docker compose --env-file .env.production -f compose.prod.yaml ps
df -h /
free -h
curl -fsS https://shopsphere-sakti.duckdns.org/nginx-health
curl -fsS -o /dev/null -w '%{http_code}\n' https://shopsphere-sakti.duckdns.org/api/v1/products
```

Healthy means the three systemd units are active, all eleven long-running containers are `Up (healthy)`, disk and memory have headroom, Nginx returns its health body, and the products API returns `200`.

## Linux command guide

| Command | What it does / why | Healthy output | Unhealthy indication |
|---|---|---|---|
| `pwd` | Prints the current directory; prevents running Compose in the wrong checkout. | `/opt/shopsphere` before production operations. | Another path can select the wrong Compose project or no environment file. |
| `ls -la` | Lists files and permissions, including hidden files; confirms expected deployment inputs exist. | Tracked source plus mode-restricted `.env.production` and `runtime/`. | Missing Compose file/runtime directory, or environment file readable by everyone. Do not print the environment file. |
| `df -h` | Shows filesystem capacity; checks root/EBS pressure before builds or backups. | Root usage comfortably below the alert threshold (currently about 20%). | More than 80% merits cleanup planning; 90%+ risks failed writes and database issues. |
| `du -sh /var/lib/docker /opt/shopsphere` | Summarizes major disk consumers; distinguishes images/volumes from source size. | Growth is explainable and enough free disk remains. | Sudden growth suggests image buildup, logs, or unexpected data. Never delete volumes blindly. |
| `free -h` | Shows RAM and swap; checks whether six JVMs and three stores have headroom. | `available` memory remains positive with no sustained pressure (about 5.1 GiB at audit). | Very low available RAM, swap thrashing, or OOM events require investigation. |
| `top` | Interactive live CPU/memory/process view; identifies active pressure. | Low load and expected Java/database/Docker processes. Press `q` to exit. | Sustained high load, runaway process, or memory exhaustion. For evidence use `top -b -n1`. |
| `ps aux --sort=-%mem | head -20` | Provides a bounded process snapshot ordered by memory. | Expected Java, MySQL, MongoDB, Docker, and SSM processes dominate. | Unknown privileged process, repeated zombie processes, or one process consuming the host. |
| `ss -tulpn` | Lists host listening sockets; confirms only Nginx is published by Docker. | Docker proxies on 80/443; internal services are absent from host/public binds. Host 22 may listen but remains SG-blocked. | Host binds for 3000, 3306, 6379, 8080–8085, 9090, or 9100 require correction. |
| `systemctl status docker --no-pager` | Shows Docker service state and recent messages. | `active (running)` without repeated failures. | Failed/inactive, crash loop, storage-driver error, or socket failure. |
| `systemctl status amazon-ssm-agent --no-pager` | Confirms the private administration path. | `active (running)` and SSM reports Online. | Inactive agent can remove remote recovery access while SSH remains closed. |
| `systemctl list-timers --all --no-pager` | Lists scheduled units; verifies certificate renewal timing. | `shopsphere-certbot-renew.timer` has a future `NEXT` run. | Missing timer, repeated failed service, or no next activation. |
| `journalctl -u docker --since '1 hour ago' --no-pager` | Reads bounded Docker daemon logs for startup/network/storage problems. | Routine startup and no repeated errors. | Overlay errors, daemon restarts, OOMs, or networking failures. |
| `journalctl -u shopsphere-certbot-renew.service -n 100 --no-pager` | Reads bounded TLS renewal history without opening key material. | Successful renewal/no-renewal-needed and successful Nginx validation/reload. | ACME, permission, validation, or reload errors. |

Use `journalctl -p warning --since today --no-pager` for a system-wide warning view. Bound output by time or line count before sharing it.

## Docker command guide

| Command | What it does / why | Healthy output | Unhealthy indication |
|---|---|---|---|
| `docker ps` | Lists running containers and health; first-line application check. | Eleven expected containers are running and healthy. | Missing container, `unhealthy`, `restarting`, or unexpected published port. |
| `docker images` | Lists local image tags/sizes; confirms deployed and rollback images exist. | Current immutable revision plus intentionally retained rollback images. | Only mutable tags, unexpected very large growth, or missing rollback image. |
| `docker logs --tail 200 SERVICE` | Reads a bounded recent service log for root-cause evidence. | Normal startup and requests without repeated errors. | Stack traces, connection refused, authentication failure, OOM, or crash loop. Never post token-bearing logs. |
| `docker inspect SERVICE` | Shows runtime configuration/state; use filtered formats so environment secrets are not emitted. | Correct network, restart policy, mounts, health, and no public port for internal services. | Wrong bind, missing named volume, unexpected capability, or failing health state. |
| `docker inspect -f '{{.HostConfig.RestartPolicy.Name}} {{if .State.Health}}{{.State.Health.Status}}{{end}}' SERVICE` | Safely extracts restart/health fields without printing environment values. | `unless-stopped healthy` for long-running services. | Empty/`no` restart policy or non-healthy state. |
| `docker stats --no-stream` | Captures current per-container CPU/memory/PIDs without an endless stream. | Usage stays within Compose limits and no sustained saturation. | Near-limit memory, sustained CPU saturation, or unusual process count. |
| `docker exec SERVICE COMMAND` | Runs a targeted diagnostic inside a container. | The smallest read-only check succeeds, such as `nginx -t`. | Command failure confirms a container/config/dependency issue. Never open a shell to print secrets. |
| `docker volume ls` | Lists persistent and anonymous volumes; confirms database volumes exist. | Named MySQL, MongoDB, and Redis volumes are present. | A named volume is missing or a deployment unexpectedly selected a new project name. |
| `docker network ls` | Lists container networks; confirms the private application network exists. | `shopsphere-prod` bridge network plus Docker defaults. | Services split across unintended networks or missing project network. |

Useful targeted checks:

```bash
docker exec shopsphere-prod-nginx-1 nginx -t
docker inspect -f '{{range .Mounts}}{{println .Name .Destination}}{{end}}' shopsphere-prod-mysql-1
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
```

## Docker Compose command guide

Always run these from `/opt/shopsphere`. Keep `.env.production` mode `0600`, and do not echo it.

| Command | What it does / why | Healthy output | Unhealthy indication |
|---|---|---|---|
| `docker compose --env-file .env.production -f compose.prod.yaml ps` | Shows project services and health using the exact production inputs. | Eleven long-running services `Up (healthy)`. | Missing, unhealthy, restarting, or exit status for a required service. |
| `docker compose --env-file .env.production -f compose.prod.yaml logs --tail 200 SERVICE` | Collects bounded logs with Compose service selection. | Normal startup/traffic and no repeating failures. | Dependency failures, exceptions, permission errors, or health timeouts. |
| `docker compose --env-file .env.production -f compose.prod.yaml config --quiet` | Validates interpolation and structure without printing expanded secrets. | Exit code 0 and no output. | Missing required variable, invalid YAML, bad mount, or invalid dependency. |
| `docker compose --env-file .env.production -f compose.prod.yaml build SERVICE` | Builds a source-based service when the selected override defines `build`; use only for a reviewed deployment. | Successful image build for the intended revision. | Compile/test/registry/disk failure. Production currently uses prebuilt/local immutable images. |
| `docker compose --env-file .env.production -f compose.prod.yaml up -d --wait` | Reconciles the existing stack non-destructively and waits for health. Named volumes remain. | Exit code 0; services reach healthy. | Timeout or failed service. Inspect logs and roll back the application revision if needed. |
| `docker compose --env-file .env.production -f compose.prod.yaml restart SERVICE` | Restarts only a selected service without changing volumes/images. | Service returns healthy and public checks pass. | Repeated restart or health failure; inspect logs and dependencies. |

Avoid restarting databases for application-only changes. Never add `-v` to a teardown command.

## AWS command guide

Run from the authenticated operator workstation with profile `shopsphere` and region `ap-south-1`.

| Command | What it does / why | Healthy output | Unhealthy indication |
|---|---|---|---|
| `aws sts get-caller-identity --profile shopsphere` | Confirms which principal will act before any AWS command. | Non-root IAM user/role expected by the operator. | Root identity or an unexpected account/principal: stop. |
| `aws ec2 describe-instances --instance-ids i-0e2cf0109097385b1 --profile shopsphere --region ap-south-1` | Reads instance state, type, addresses, subnet, SG, AMI, and instance profile. | Existing instance is `running`, with expected IDs and EIP. | Unexpected instance ID/type/state/network, missing IAM profile, or changed IP association. |
| `aws ec2 describe-instance-status --include-all-instances --instance-ids i-0e2cf0109097385b1 --profile shopsphere --region ap-south-1` | Reads EC2 system and instance reachability checks. | Both statuses `ok`. | `impaired`, `insufficient-data`, or prolonged initializing. |
| `aws ec2 describe-volumes --volume-ids vol-0ed9fa47e65c3fd84 --profile shopsphere --region ap-south-1` | Verifies persistent root EBS identity, attachment, encryption, size, and health. | Same encrypted 40 GiB gp3 volume, `in-use`, attached to the expected instance. | Detachment, wrong instance, non-encrypted disk, or unexpected replacement. |
| `aws ec2 describe-security-groups --group-ids sg-037cf17baa6b1070a --profile shopsphere --region ap-south-1` | Verifies Internet exposure and egress. | Ingress is only TCP 80/443 from IPv4 Internet; no SSH/database/service rules. | Any unintended port/CIDR, especially 22, database, Docker, monitoring, or microservice ports. |
| `aws ssm describe-instance-information --filters Key=InstanceIds,Values=i-0e2cf0109097385b1 --profile shopsphere --region ap-south-1` | Verifies the SSM control channel used instead of SSH. | `PingStatus` is `Online`. | Offline/stale agent blocks remote administration and deployment. |

Stopping EC2 pauses instance compute charges but leaves EBS and Elastic IPv4 charges. Starting it again retains the EIP and volumes. Confirm application health after every start.

## Terraform command guide

Terraform is inspected locally in `terraform/`. Pass the exact reviewed repository SHA dynamically; never save the real SHA in tracked tfvars.

| Command | What it does / why | Healthy output | Unhealthy indication |
|---|---|---|---|
| `terraform fmt -check -recursive` | Checks canonical formatting without changing infrastructure. | Exit code 0. | Listed files need `terraform fmt` before commit. |
| `terraform validate` | Validates configuration/provider schema using the initialized directory. | `Success! The configuration is valid.` | Syntax, type, provider, or module errors. |
| `terraform plan -var="repository_ref=$SHA"` | Refreshes state and previews exact actions; this is the mandatory safety gate. | No destructive action; normally `0 to add, 0 to change, 0 to destroy`. | EC2/EBS replacement, deletion, AMI/user-data drift, or unrelated change: stop and investigate. |
| `terraform state list` | Lists resources tracked in the selected state; confirms scope and addresses. | Expected VPC, subnet, route, SG, IAM, EC2, EIP resources. | Missing/duplicate addresses or a wrong workspace/backend. |
| `terraform show` | Displays current state or a saved plan for review. | Values match actual existing production resources. | Secrets in output require careful handling; mismatch suggests drift. |
| `terraform output` | Prints declared non-secret operational outputs such as endpoint/IDs. | Expected EIP, instance ID, and public ports. | Missing/stale outputs can indicate wrong state. Do not declare secret outputs. |

Never apply an unreviewed plan. State and plan files are local sensitive artifacts and must remain Git-ignored.

## Application and dependency checks

```bash
curl -fsS -o /dev/null -w 'site=%{http_code}\n' https://shopsphere-sakti.duckdns.org/
curl -fsS -o /dev/null -w 'nginx=%{http_code}\n' https://shopsphere-sakti.duckdns.org/nginx-health
curl -fsS -o /dev/null -w 'products=%{http_code}\n' https://shopsphere-sakti.duckdns.org/api/v1/products
curl -sS -o /dev/null -w 'redirect=%{http_code} %{redirect_url}\n' http://shopsphere-sakti.duckdns.org/
```

Healthy is HTTPS `200` for all three endpoints and HTTP `301` to the HTTPS hostname. A `502` points to an Nginx/upstream problem; `503` often points to gateway fallback/dependency health; `401/403` is expected only on protected operations.

Database connectivity is proven through service health and bounded logs. For direct inspection, use read-only counts and container-provided credentials internally without echoing passwords. Prefer the backup scripts and documented restore test over improvised database commands.

## Safe deploy and rollback workflow

1. Confirm Git revision, clean worktree, free disk, backup status, and all current health checks.
2. Record `PREVIOUS_REVISION=$(git rev-parse HEAD)` in the operator session; do not commit it.
3. Fetch and verify the exact approved 40-character commit.
4. Preserve `.env.production`, `runtime/certbot`, `runtime/tls`, and named volumes.
5. Build/reconcile only affected application containers where practical.
6. Run `docker compose --env-file .env.production -f compose.prod.yaml up -d --wait`.
7. Verify Nginx config, container health, HTTPS endpoints, customer smoke flow, and data counts.
8. If health fails, inspect bounded logs. Restore the previous application revision and images, run the same non-destructive Compose command, and verify old health.
9. Never reverse database data automatically. A schema-incompatible rollback requires a reviewed forward fix or restore plan.

## Common incident decision tree

1. Public endpoint down: check EC2 state/status, SSM, SG, then `systemctl status docker`.
2. Nginx `502`: run `nginx -t`, inspect Nginx and gateway health/logs, then dependency containers.
3. API `500`: use correlation ID and bounded service logs; check database/container health and free disk.
4. Container restarting: inspect health state, exit code, OOM flag, logs, image revision, mounts, and dependency health.
5. Database problem: stop application deploy activity, preserve volumes, take/verify backup if possible, and diagnose credentials/schema/storage without reinitializing data.
6. TLS problem: verify DNS/EIP, certificate dates/mounts, ACME challenge route, renewal service logs, and `nginx -t`; do not expose key material.

After any recovery, verify all eleven containers, public HTTPS checks, product/user/order counts, security-group ingress, and Soundbox remains stopped.

