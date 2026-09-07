# ShopSphere DevOps Phase Status

This ledger records evidence for the production-operations work performed against the existing ShopSphere deployment. A phase is marked complete only after its repository changes are tested, committed, pushed, and—where runtime behavior changes—verified through the public HTTPS endpoint.

| Phase | Status | Changes | Tests | Commit | Production status | Notes |
|---|---|---|---|---|---|---|
| 4 — Production Operations | Complete | Read-only production audit and comprehensive operator runbook | AWS/SSM inventory passed; 11/11 containers healthy; HTTPS, Nginx health and products API HTTP 200; secret hygiene passed | `61fa1e8456141361ef6838cd208eed4965f9433d` | Healthy | Existing EC2/EBS/EIP preserved; Soundbox stopped |
| 5 — CI/CD | Complete (configuration) | Compose/shell CI gate; approval-gated OIDC/SSM deploy workflow; exact-revision deploy and rollback script; CI/CD guide | Bash syntax, Compose model, workflow YAML and secret hygiene passed locally | `85056b8` | Unchanged; deployment not run | OIDC role/GitHub Environment require reviewed external setup; image publication currently blocked by Phase 8 vulnerability gate |
| 6 — Monitoring & Observability | Complete | Internal Prometheus/Grafana plus Node Exporter/cAdvisor; five provisioned dashboards; host/container/application alerts; operator guide; internal Product metrics authorization fix | Compose/JSON/promtool validation; 15/15 production containers healthy; 8/8 Prometheus targets up; 10 alert rules loaded; public health HTTP 200 | `1f88055`, `9fedb34` | Healthy | No new AWS resources or public ports; Grafana/Prometheus bind only to loopback and exporters have no host ports |
| 7 — Backup & Recovery | Complete | Compressed backups, retention and record fingerprints; isolated restore workflow | 2026-09-07: MySQL 3 databases/11 tables and MongoDB 3 collections/43 documents restored; all count and content fingerprints matched | `4e3f1c7`, `e2121e8` | Application recovered; production volumes preserved | Format v2 backup `20260907T025837Z`; commits pushed |
| 8 — DevSecOps Hardening | Complete | Runtime package fixes, Tomcat 10.1.59, removal of runtime npm, host telemetry replacing privileged cAdvisor | CI 34101039768 passed including all eight image gates; 14/14 healthy, 7/7 targets up, 12 rules; three HTTPS endpoints 200; database IDs/mounts and counts preserved | `5d3259f`, `668d022` | All eight application images deployed at `5d3259f` | SSM deployment 046bdfe3-58a5-4cbd-87dc-5bdbbb46fcf7; no AWS infrastructure change |
| 9 — Failure & Troubleshooting Drills | Complete | Six bounded drills and repeatable operator script | Recovery, three HTTPS endpoints, 14 container health states and data-count/inventory comparisons passed after each | See phase-specific troubleshooting commit | Healthy | Operator kill needed explicit start; bad-deployment and DB failures isolated; no volume removal |
| 10 — Final Documentation | Pending | — | — | — | Unchanged | Documentation must reflect verified implementation |
| 11 — Interview Preparation | Pending | — | — | — | Unchanged | Answers must be based on real evidence |

## Immutable safety baseline

- AWS region: `ap-south-1`
- ShopSphere EC2: `i-0e2cf0109097385b1`
- Root EBS: `vol-0ed9fa47e65c3fd84`
- Elastic IP: `65.2.225.30`
- Public endpoint: `https://shopsphere-sakti.duckdns.org`
- Public ingress: TCP 80 and TCP 443 only
- Administration: AWS Systems Manager; no public SSH rule
- Soundbox EC2 `i-00759e44316d2c460` must remain stopped and untouched
- Never use `terraform destroy`, terminate/replace EC2, delete EBS, or run `docker compose down -v`

## Phase 4 evidence snapshot

Captured 2026-09-06 through read-only AWS APIs and an SSM command:

- EC2 state `running`; AWS system and instance checks `ok`; SSM `Online`.
- `m7i-flex.large`, Amazon Linux, IMDSv2 required, encrypted 40 GiB gp3 root disk.
- Root filesystem 20% used (about 33 GiB available); 5.1 GiB memory available; no swap.
- Docker and the certificate-renewal timer active.
- Eleven expected application/data containers running and healthy.
- Named volumes present for MySQL, MongoDB, and Redis.
- Only Nginx publishes host ports 80/443. Internal service/database ports stay on the Docker network.
- The host SSH daemon listens locally, but the security group has no TCP 22 ingress; operators use SSM.
- HTTPS site, Nginx health endpoint, and products API returned HTTP 200 after the host start.
