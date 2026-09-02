package com.shopsphere.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class CorrelationFilterTest {
  @Test void generatesAndPropagatesCorrelationId() {
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test").build());
    GatewayFilterChain chain = current -> { assertThat(current.getRequest().getHeaders().getFirst(GatewayConfiguration.CORRELATION_ID)).isNotBlank(); return Mono.empty(); };
    new GatewayConfiguration().correlationAndLoggingFilter().filter(exchange, chain).block();
    assertThat(exchange.getResponse().getHeaders().getFirst(GatewayConfiguration.CORRELATION_ID)).isNotBlank();
  }

  @Test void preservesValidIncomingCorrelationId() {
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test").header(GatewayConfiguration.CORRELATION_ID, "qa-123").build());
    new GatewayConfiguration().correlationAndLoggingFilter().filter(exchange, current -> Mono.empty()).block();
    assertThat(exchange.getResponse().getHeaders().getFirst(GatewayConfiguration.CORRELATION_ID)).isEqualTo("qa-123");
  }
}
