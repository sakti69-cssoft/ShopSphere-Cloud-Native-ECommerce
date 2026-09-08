# ShopSphere DevOps Phase Status

This ledger records evidence for the production-operations work performed against the existing ShopSphere deployment. A phase is marked complete only after its repository changes are tested, committed, pushed, and—where runtime behavior changes—verified through the public HTTPS endpoint.

| Phase | Status | Changes | Tests | Commit | Production status | Notes |
|---|---|---|---|---|---|---|
| 4 — Production Operations | Complete | Read-only production audit and comprehensive operator runbook | AWS/SSM inventory passed; 11/11 containers healthy; HTTPS, Nginx health and products API HTTP 200; secret hygiene passed | `61fa1e8456141361ef6838cd208eed4965f9433d` | Healthy | Existing EC2/EBS/EIP preserved; Soundbox stopped |
| 5 — CI/CD | Complete (configuration) | Approval-gated OIDC/SSM workflow and immutable image delivery | CI 34101039768 passed all eight image gates; operator SSM deployment verified | `85056b8`, `5d3259f` | Healthy | GitHub OIDC role/Environment still require external setup |
| 6 — Monitoring & Observability | Complete | Prometheus, Grafana, Node Exporter and host textfile metrics; five dashboards | 14/14 containers healthy; 7/7 targets up; 12 rules loaded | `1f88055`, `9fedb34`, `5d3259f`, `668d022` | Healthy | cAdvisor removed in Phase 8; no public monitoring ports |
| 7 — Backup & Recovery | Complete | Compressed backups, retention and record fingerprints; isolated restore workflow | 2026-09-07: MySQL 3 databases/11 tables and MongoDB 3 collections/43 documents restored; all count and content fingerprints matched | `4e3f1c7`, `e2121e8` | Application recovered; production volumes preserved | Format v2 backup `20260907T025837Z`; commits pushed |
| 8 — DevSecOps Hardening | Complete | Runtime package fixes, Tomcat 10.1.59, removal of runtime npm, host telemetry replacing privileged cAdvisor | CI 34101039768 passed including all eight image gates; 14/14 healthy, 7/7 targets up, 12 rules; three HTTPS endpoints 200; database IDs/mounts and counts preserved | `5d3259f`, `668d022` | All eight application images deployed at `5d3259f` | SSM deployment 046bdfe3-58a5-4cbd-87dc-5bdbbb46fcf7; no AWS infrastructure change |
| 9 — Failure & Troubleshooting Drills | Complete | Six bounded drills and repeatable operator script | Recovery, three HTTPS endpoints, 14 container health states and data-count/inventory comparisons passed after each | See phase-specific troubleshooting commit | Healthy | Operator kill needed explicit start; bad-deployment and DB failures isolated; no volume removal |
| 10 — Final Documentation | Complete | README, architecture, deployment and operational evidence aligned to live AWS/HTTPS/monitoring/security | Documentation diff and links reviewed; runtime evidence from phases 4–9 | See documentation commit | Healthy | OIDC setup, single-host and local-backup limitations explicit |
| 11 — Interview Preparation | Complete | Ten interview guides, command reference, STAR incidents and four pitch lengths | Evidence reviewed against phases 4–10; secret hygiene and diff checks passed | See interview phase commit | Documentation only | OIDC, isolated drills, local backups and single-host limits explicit |

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

## 2026-09-08 restart and plan verification

The existing ShopSphere instance was started after the overnight pause. System
and instance checks passed, SSM was Online, all fourteen containers were healthy,
seven scrape targets were up and twelve rules loaded. Public HTTPS website,
Nginx health and products API returned 200. Counts were 41 products, 41 inventory
records, twelve users (including yesterday's synthetic test user) and two orders.
Soundbox remained stopped.

Terraform fmt and validate passed. An initial plan using a historical saved-plan
SHA was rejected because user_data would replace EC2. The actual state-compatible
bootstrap SHA is `5fc43f01070ae24b2ea15022802ca135a9747f43`. Using it produced
zero resource additions/changes/destructions; only the public-DNS output refreshes
to the existing Elastic IP. Neither plan was applied. Application image revision
is a separate concern and remains `5d3259f`.

Final browser checkout/order verification remains outstanding; do not treat
phase-document completion as certification of every customer workflow.
