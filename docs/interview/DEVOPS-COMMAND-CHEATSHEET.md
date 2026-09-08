# ShopSphere DevOps Command Cheatsheet

Run AWS commands from an authenticated operator workstation with profile `shopsphere` and region `ap-south-1`. Run host commands through SSM from `/opt/shopsphere`. Never print `.env.production`, certificate keys, tokens, or expanded Compose environment values.

## AWS identity and instance health

| Command | Why / healthy result |
|---|---|
| `aws sts get-caller-identity --profile shopsphere` | Confirm a non-root expected principal before acting. |
| `aws ec2 describe-instances --instance-ids i-0e2cf0109097385b1 --profile shopsphere --region ap-south-1` | Confirm the same instance, type, state, AMI, SG, EIP association, and role. |
| `aws ec2 describe-instance-status --include-all-instances --instance-ids i-0e2cf0109097385b1 --profile shopsphere --region ap-south-1` | Healthy production has system and instance status `ok`. |
| `aws ssm describe-instance-information --filters Key=InstanceIds,Values=i-0e2cf0109097385b1 --profile shopsphere --region ap-south-1` | SSM should report `Online`. |
| `aws ec2 describe-volumes --volume-ids vol-0ed9fa47e65c3fd84 --profile shopsphere --region ap-south-1` | Verify the same encrypted 40 GiB gp3 volume remains attached. |
| `aws ec2 describe-security-groups --group-ids sg-037cf17baa6b1070a --profile shopsphere --region ap-south-1` | Only public TCP 80/443 should appear; no SSH, DB, Docker, service, or monitoring ports. |
| `aws ssm start-session --target i-0e2cf0109097385b1 --profile shopsphere --region ap-south-1` | Open an IAM-authorized shell without public SSH. |

## Linux triage

| Command | What to learn |
|---|---|
| `pwd` and `ls -la` | Confirm `/opt/shopsphere` and expected deployment inputs; do not display environment contents. |
| `df -h /` | Root filesystem utilization and free capacity. |
| `du -sh /var/lib/docker /opt/shopsphere` | Bound the main disk consumers before cleanup decisions. |
| `free -h` | Available memory and swap pressure. |
| `top -b -n1` | One bounded CPU/memory/load snapshot. |
| `ps aux --sort=-%mem | head -20` | Largest memory consumers. |
| `ss -tulpn` | Host listeners; expected public Docker binds are 80/443 only. |
| `systemctl is-active docker amazon-ssm-agent shopsphere-certbot-renew.timer` | Docker, remote management, and TLS-renewal readiness. |
| `journalctl -u docker --since '1 hour ago' --no-pager` | Docker daemon startup, storage, network, or restart evidence. |
| `journalctl -p warning --since today --no-pager` | Bounded system-wide warnings. |

## Docker and Compose

```bash
cd /opt/shopsphere
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
docker stats --no-stream
docker images
docker volume ls
docker network ls
docker compose --env-file .env.production -f compose.prod.yaml ps
docker compose --env-file .env.production -f compose.prod.yaml config --quiet
```

Healthy means the eleven long-running application/data containers are up and healthy, the three named database volumes exist, and only Nginx publishes 80/443.

Targeted diagnostics:

```bash
docker compose --env-file .env.production -f compose.prod.yaml logs --tail 200 gateway
docker inspect -f '{{.HostConfig.RestartPolicy.Name}} {{if .State.Health}}{{.State.Health.Status}}{{end}}' shopsphere-prod-gateway-1
docker compose --env-file .env.production -f compose.prod.yaml exec -T nginx nginx -t
docker inspect -f '{{range .Mounts}}{{println .Name .Destination}}{{end}}' shopsphere-prod-mysql-1
```

Non-destructive reconciliation:

```bash
docker compose --env-file .env.production -f compose.prod.yaml up -d --wait --wait-timeout 300
```

Never use `docker compose down -v`, never prune volumes blindly, and avoid restarting databases for application-only changes.

## Public health and TLS

```bash
curl -fsS -o /dev/null -w '%{http_code}\n' https://shopsphere-sakti.duckdns.org/
curl -fsS -o /dev/null -w '%{http_code}\n' https://shopsphere-sakti.duckdns.org/nginx-health
curl -fsS -o /dev/null -w '%{http_code}\n' https://shopsphere-sakti.duckdns.org/api/v1/products
curl -sS -o /dev/null -w '%{http_code} %{redirect_url}\n' http://shopsphere-sakti.duckdns.org/
openssl s_client -connect shopsphere-sakti.duckdns.org:443 -servername shopsphere-sakti.duckdns.org </dev/null
```

Expected: three HTTPS `200` responses, HTTP redirects to HTTPS, and a trusted certificate matching the hostname. Do not paste private-key files into diagnostics.

## Terraform safety gate

From `terraform/`, pass the reviewed Git SHA dynamically. Do not store the real SHA in tracked `terraform.tfvars`.

```powershell
terraform fmt -check -recursive
terraform init -backend=false -input=false
terraform validate
$sha = git rev-parse HEAD
terraform plan -var="repository_ref=$sha"
terraform state list
terraform output
```

`fmt` and `validate` check source; `plan` previews; only `apply` mutates AWS. Stop if a plan shows EC2/EBS replacement, destruction, AMI drift, user-data replacement, or unrelated change.

## Git release checks

```bash
git status --short
git branch --show-current
git rev-parse HEAD
git fetch origin main
git merge-base --is-ancestor HEAD origin/main
git log -1 --oneline
```

A deploy candidate should be an exact 40-character commit on `origin/main`, with a clean tracked checkout. Do not force-push production history.

## CI/CD investigation

```bash
bash -n scripts/*.sh
node scripts/check-secrets.mjs
docker compose --env-file .env.production.example -f compose.prod.yaml -f compose.aws-build.yaml config --quiet
```

In GitHub Actions, inspect the first failing gate rather than rerunning blindly. An image scan failure means the image is not eligible for publication; it is not evidence that CD or production deployment succeeded.

## Monitoring commands — use only after Phase 6 deployment is verified

Prometheus, Grafana and Node Exporter are deployed alongside eleven application/data containers. Five dashboards and twelve rules are provisioned; seven targets were verified up. A root-owned host timer collects selected Docker statistics into Node Exporter's read-only textfile mount. Privileged cAdvisor was removed and the host runtime socket paths are masked from Node Exporter. External notification delivery is not configured.

```bash
docker compose --env-file .env.production -f compose.prod.yaml -f compose.observability.yaml --profile observability ps
curl -fsS http://127.0.0.1:9090/-/healthy
curl -fsS http://127.0.0.1:3001/api/health
curl -fsS http://127.0.0.1:9090/api/v1/targets
curl -fsS http://127.0.0.1:9090/api/v1/rules
```

Access Grafana with SSM port forwarding, never a new public SG rule:

```bash
aws ssm start-session --target i-0e2cf0109097385b1 --document-name AWS-StartPortForwardingSession --parameters '{"portNumber":["3001"],"localPortNumber":["3001"]}' --profile shopsphere --region ap-south-1
```

## Incident sequence

Use this order under pressure:

1. Confirm scope and last change.
2. Check public health and AWS instance/SSM health.
3. Check Compose/container state and bounded logs.
4. Check host CPU, memory, disk, and listeners.
5. Test the failing dependency from inside the relevant network boundary.
6. Form one hypothesis and make the smallest reversible correction.
7. Recheck health, customer path, persistence, and security exposure.
8. Record evidence and prevention; never use destructive cleanup as a shortcut.
