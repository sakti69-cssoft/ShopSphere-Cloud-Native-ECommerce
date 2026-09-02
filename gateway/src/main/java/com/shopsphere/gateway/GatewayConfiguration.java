package com.shopsphere.gateway;

import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

@Configuration
class GatewayConfiguration {
  static final String CORRELATION_ID = "X-Correlation-ID";
  private static final Logger log = LoggerFactory.getLogger(GatewayConfiguration.class);

  @Bean KeyResolver clientKeyResolver() {
    return exchange -> exchange.getPrincipal().map(p -> "user:" + p.getName())
        .switchIfEmpty(Mono.fromSupplier(() -> "ip:" + java.util.Optional.ofNullable(exchange.getRequest().getRemoteAddress())
            .map(a -> a.getAddress().getHostAddress()).orElse("unknown")));
  }

  @Bean @Order(Ordered.HIGHEST_PRECEDENCE) GlobalFilter correlationAndLoggingFilter() {
    return (exchange, chain) -> {
      long started = System.nanoTime();
      String incoming = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID);
      String id = incoming != null && incoming.matches("[A-Za-z0-9._-]{1,100}") ? incoming : UUID.randomUUID().toString();
      var request = exchange.getRequest().mutate().headers(h -> h.set(CORRELATION_ID, id)).build();
      exchange.getResponse().getHeaders().set(CORRELATION_ID, id);
      return chain.filter(exchange.mutate().request(request).build()).doFinally(signal -> {
        var status = exchange.getResponse().getStatusCode();
        log.info("gateway_request method={} path={} status={} latencyMs={} correlationId={}",
            request.getMethod(), request.getPath().value(), status == null ? 0 : status.value(),
            Duration.ofNanos(System.nanoTime() - started).toMillis(), id);
      });
    };
  }

  @Bean org.springframework.web.server.WebFilter corsWebFilter(@org.springframework.beans.factory.annotation.Value("${shopsphere.cors.allowed-origins}") String origins) { return new OrderedCorsFilter(origins); }

  private static final class OrderedCorsFilter implements org.springframework.web.server.WebFilter, Ordered {
    private final java.util.Set<String> origins;
    private OrderedCorsFilter(String configured) { origins = java.util.Arrays.stream(configured.split(",")).map(String::trim).filter(s -> !s.isBlank()).collect(java.util.stream.Collectors.toUnmodifiableSet()); }
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }
    public Mono<Void> filter(org.springframework.web.server.ServerWebExchange exchange, org.springframework.web.server.WebFilterChain chain) {
      String origin = exchange.getRequest().getHeaders().getOrigin();
      if (origin == null) return chain.filter(exchange);
      if (!origins.contains(origin)) { exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN); return exchange.getResponse().setComplete(); }
      var headers = exchange.getResponse().getHeaders();headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,origin);headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,"true");headers.set(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,CORRELATION_ID);headers.add(HttpHeaders.VARY,HttpHeaders.ORIGIN);
      if (exchange.getRequest().getMethod() == org.springframework.http.HttpMethod.OPTIONS) {headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,"GET,POST,PUT,PATCH,DELETE,OPTIONS");String requested=exchange.getRequest().getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS);if(requested!=null)headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,requested);headers.set(HttpHeaders.ACCESS_CONTROL_MAX_AGE,"3600");exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.NO_CONTENT);return exchange.getResponse().setComplete();}
      return chain.filter(exchange);
    }
  }

}
