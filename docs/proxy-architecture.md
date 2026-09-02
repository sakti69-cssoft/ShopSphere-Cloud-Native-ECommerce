# Proxy architecture

The primary ShopSphere request path uses reverse proxies:

```text
Client -> Nginx reverse proxy -> Spring Cloud Gateway -> internal services
```

Nginx is the public-facing local edge. It forwards only `/api/` to the gateway, supplies `Host`, `X-Forwarded-For`, `X-Forwarded-Proto`, and `X-Correlation-ID`, applies timeouts and body limits, enables gzip, and adds basic browser security headers. Backend service ports remain published for local debugging, but production security groups/network policies should expose only the load balancer or Nginx edge; gateway and service ports should remain private.

A forward proxy serves a different direction:

```text
Internal workload -> forward proxy -> Internet
```

No forward proxy is inserted into ShopSphere's primary request path because the current architecture does not require one. It can later enforce allow-listed outbound access and auditing for workloads in private AWS subnets. This phase creates no AWS resources.
