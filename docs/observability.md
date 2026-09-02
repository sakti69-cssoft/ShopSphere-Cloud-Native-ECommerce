# Observability
All Java services include Micrometer Prometheus and expose /actuator/health and /actuator/prometheus internally. Nginx forwards only /api, so actuator endpoints are not published by the production edge. No secrets/health details are exposed.

Prometheus scrapes six applications every 15 seconds and retains seven days. Grafana provisioning loads the Prometheus datasource and ShopSphere Overview dashboard: per-instance availability, throughput, p95 histogram latency, gateway routes, JVM heap/threads, CPU, 5xx and circuit breakers. The instance labels distinguish Auth, Product, Inventory, Cart, Order and Gateway. Empty request panels before traffic are expected.

HTTP server histograms are enabled; they increase metric cardinality/memory. Avoid uncontrolled path labels. Example alerts: service down 2m; 5xx >5% for 5m; p95 >2s for 5m; heap >85% for 10m; gateway failures >1/sec for 2m. Tune using actual workloads; these are portfolio thresholds, not SLO guarantees. No Alertmanager notification destination is configured.

Container/database exporters, distributed tracing and centralized logs are not included. Gateway logs carry validated correlation IDs and no request bodies/tokens. The dashboard shows JVM memory, not full host/container resident memory.
