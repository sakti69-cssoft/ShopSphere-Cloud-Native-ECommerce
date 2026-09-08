# ShopSphere Monitoring Interview Q&A

## Status boundary

Prometheus, Grafana and Node Exporter are deployed alongside eleven application/data containers. Five dashboards and twelve rules are provisioned; seven targets were verified up. A root-owned host timer collects selected Docker statistics into Node Exporter's read-only textfile mount. Privileged cAdvisor was removed and the host runtime socket paths are masked from Node Exporter. External notification delivery is not configured.

## Metrics versus logs versus traces

- Metrics are aggregated numeric time series suited to rates, thresholds, trends, and alerts.
- Logs are event records suited to details, errors, and correlation-ID investigation.
- Traces follow a request across service boundaries and expose distributed latency.

ShopSphere has application/host/container metric configuration and container/system logs. It does not yet claim centralized long-term logs or distributed tracing.

## Prometheus versus Grafana

Prometheus scrapes metrics, stores time series, evaluates PromQL, and runs alert rules. Grafana queries Prometheus and presents dashboards. Grafana is not the source of the metrics and Prometheus is not an off-host backup.

## What would ShopSphere scrape?

- Gateway and five Java services at internal `/actuator/prometheus` endpoints.
- Node Exporter for host CPU, RAM, filesystem, load, and network.
- Host Docker-statistics timer through Node Exporter for container CPU, memory, uptime, health and restart counts.

Targets are addressed over the private Docker network. Actuator/exporter endpoints are not routed through public Nginx.

## What are the RED and USE methods?

RED is service-centric: **Rate**, **Errors**, **Duration**. For ShopSphere that means request throughput, 5xx ratio, and p95 latency by service/route.

USE is resource-centric: **Utilization**, **Saturation**, **Errors**. For the EC2/container layer that means CPU/memory/disk usage, load/queue or limit pressure, and OS/container failures.

Together they help avoid staring at CPU while customers see checkout errors, or staring at HTTP latency while the disk is nearly full.

## What dashboards are configured?

1. ShopSphere Overview: availability, requests, p95 latency, Gateway routes, JVM and failures.
2. Host / EC2: CPU, memory, root disk, load, and network.
3. Containers: CPU, memory versus limit, uptime, health and restart counts.
4. JVM / Microservices: heap, threads, process CPU, GC, classes, and uptime.
5. HTTP / API: throughput, status/5xx ratio, p95 latency, routes, and circuit breakers.

Provisioned JSON makes dashboard intent reviewable. A populated panel still requires a live target, correct labels, representative traffic, and at least two scrape intervals.

## Which alerts are configured?

The source defines service/monitoring target unavailable, HTTP 5xx ratio over 5%, p95 over two seconds, JVM heap over 85%, Gateway downstream failure spike, host CPU/memory over 85%, root disk below 15% free, and container memory over 90% of its limit. Durations reduce transient noise. These are initial portfolio thresholds, not measured SLOs.

## Why not expose Grafana publicly?

Monitoring can reveal topology, paths, versions, load, and failure states. Grafana and Prometheus bind to `127.0.0.1`; exporters stay only on the Docker network. Operators should use SSM port forwarding to localhost. No SG rule should be added for 3000/3001/8080–8085/9090/9100.

## How do you access Grafana?

Use `AWS-StartPortForwardingSession` through SSM from local port 3001 to EC2 loopback 3001, then authenticate locally with the instance-stored administrator credential. Do not put that password in Git, shell history, workflow input, or interview output.

## Why seven-day Prometheus retention?

The monitoring database shares the 40 GiB root EBS with the application. Seven days constrains local disk growth for a demo. It sacrifices history and survives only while the EBS/volume survives; production requirements may justify remote write or managed long-term storage.

## What is alert fatigue?

Poorly scoped thresholds generate noisy, unactionable pages. ShopSphere uses `for` durations and separates warning/critical intent, but real traffic baselines must tune thresholds. Every alert needs an owner, customer impact, diagnostic runbook, and recovery action.

## Are notifications configured?

No external Alertmanager email/Slack/PagerDuty receiver is claimed because no approved destination credential exists. Prometheus rules can still be evaluated and inspected. A firing rule without an external receiver is not end-to-end alerting.

## What is metric cardinality?

Every unique label set becomes a time series. User IDs, order IDs, URLs with unbounded IDs, tokens, and raw exception text can explode storage/memory and leak sensitive data. Use bounded labels such as route templates, method, status class, service, and environment.

## How would you investigate high latency?

1. Confirm rate/error/duration and whether one route/service is affected.
2. Check Gateway/downstream timeouts and circuit breakers.
3. Compare JVM heap/GC/thread and process CPU.
4. Check container CPU/memory limits and host USE signals.
5. Check database latency/connections and disk pressure.
6. Follow correlation IDs through bounded logs.
7. Compare with deployment/config changes.

Do not restart every container before collecting evidence.

## Why was cAdvisor replaced?

Prometheus, Grafana and Node Exporter are deployed alongside eleven application/data containers. Five dashboards and twelve rules are provisioned; seven targets were verified up. A root-owned host timer collects selected Docker statistics into Node Exporter's read-only textfile mount. Privileged cAdvisor was removed and the host runtime socket paths are masked from Node Exporter. External notification delivery is not configured.

## How would you verify monitoring after deployment?

Check three monitoring containers healthy, Prometheus and Grafana localhost health, all scrape targets up, rules loaded, dashboard data after representative traffic, resource headroom, no new host/public listeners, and the original eleven application/data containers still healthy. Then safely trigger/recover a non-production-impacting alert or validate expressions with synthetic series.

## What are the biggest limitations?

Monitoring shares the application host/failure domain, has short local retention, no external notification receiver, no centralized logs, no tracing, and no proven SLO. A host outage can remove both service and telemetry. State these limitations before proposing managed systems.
