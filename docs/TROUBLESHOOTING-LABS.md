# ShopSphere troubleshooting labs

Executed 2026-09-07 on the existing host through SSM. Each drill checked all
14 containers and three public HTTPS endpoints afterward, and compared user,
order and product counts plus inventory available/reserved totals before/after.
All comparisons passed. These checks do not prove every database byte unchanged.

Run only one explicitly selected drill through an authorized maintenance session:
`bash scripts/troubleshooting-drill.sh crash|service|deployment|database|disk|cpu`
(choose one value, not the pipe-separated expression). The script targets
ShopSphere only, bounds load, cleans its own temporary resources and starts the
existing Product container on exit when applicable. It never removes volumes.

| Drill | Symptom and evidence | Root cause / recovery | Validation |
|---|---|---|---|
| Forced container stop | Product killed by Docker; restart count stayed 0 | Operator kill did not demonstrate automatic policy recovery; explicitly started the same container | All containers/HTTPS/data comparisons passed |
| Stateless service unavailable | Public products API returned 503 while Product was stopped | Gateway could not reach its dependency; started same Product container | Endpoint recovered, data totals unchanged |
| Bad deployment | Isolated candidate healthcheck returned unhealthy | Intentionally failing health command; recreated only disposable candidate with previous healthy configuration | Candidate healthy; live stack untouched |
| Database connectivity | Isolated MySQL client returned ERROR 2003 (HY000) | Network-none client had no database on loopback; removed disposable client | Live database and HTTPS remained healthy |
| Disk pressure | 16 MiB temporary file; root filesystem 24% used with 31 GiB available | Bounded allocation demonstrated df/du; removed exact temporary file | Free-space display returned unchanged; data preserved |
| CPU/memory investigation | Docker stats and free showed about 4.9 GiB available; three-second single-core loop | Short bounded load, not sustained stress | Loop completed; containers/HTTPS/data remained healthy |

SSM evidence: crash `2dacd61e-e5e0-4e3b-92a4-8ea700553146`, service
`9e4e9dae-e5fa-4686-acc3-709e7a06ab38`, remaining sequential drills
`fbd0265b-d919-4abf-9b89-9d1ef03c0761` all completed successfully.
An initial in-container signal attempt did not stop PID 1 and was not counted
as a successful failure injection.

## Practical diagnosis

Use `docker ps -a` and selected `docker inspect` state/health/restart fields to
distinguish stopped, restarting and unhealthy. A restart policy does not make
an unhealthy-but-running container restart automatically. Check bounded
`docker logs --since 5m --tail 100` locally for startup/dependency errors;
redact sensitive details before sharing logs. Correlate gateway 503 with the
dependency state before changing Nginx or opening network ports.

For a database connection error, first distinguish name resolution, route,
listener and authentication problems. The isolated ERROR 2003 demonstrated a
missing connection, not a credential failure. Never reset a database volume
to solve a connection problem. For disk issues use `df -h`, `du -h` on known
paths and retention policies; do not prune named volumes. For resource issues
compare `docker stats --no-stream`, `free -m`, host load and monitoring trends.

## Interview explanation and limits

“I injected bounded failures, observed the real response, recovered the same
service and rechecked data. Product unavailability produced 503. An operator
kill required explicit recovery in this drill, so I did not claim automatic
self-healing. Candidate rollback and database failures were isolated to avoid
corrupting live data.”

The isolated candidate test proves the health-gate/recovery concept, not a
successful GitHub OIDC pipeline rollback. The disk and CPU tests are controlled
demonstrations, not capacity benchmarks or disk-exhaustion tests. Temporary
containers and the 16 MiB file were removed; no application volume was removed.
