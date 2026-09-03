package com.shopsphere.gateway;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

@Configuration @EnableWebFluxSecurity
class GatewaySecurity {
  @Bean ReactiveJwtDecoder jwtDecoder(@Value("${shopsphere.jwt.secret}") String secret) {
    var key = new SecretKeySpec(Arrays.copyOf(secret.getBytes(StandardCharsets.UTF_8), 32), "HmacSHA256");
    var decoder = NimbusReactiveJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    decoder.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(org.springframework.security.oauth2.jwt.JwtValidators.createDefaultWithIssuer("shopsphere-auth"), jwt -> "access".equals(jwt.getClaimAsString("type")) ? org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success() : org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(new org.springframework.security.oauth2.core.OAuth2Error("invalid_token"))));
    return decoder;
  }

  @Bean SecurityWebFilterChain security(ServerHttpSecurity http, ReactiveJwtDecoder decoder) {
    var roles = new JwtAuthenticationConverter();
    roles.setJwtGrantedAuthoritiesConverter(jwt -> java.util.List.of(
        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString("role"))));
    return http.csrf(ServerHttpSecurity.CsrfSpec::disable).cors(ServerHttpSecurity.CorsSpec::disable)
        .authorizeExchange(auth -> auth
            .pathMatchers("/actuator/prometheus","/actuator/health", "/actuator/info", "/fallback/**").permitAll()
            .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .pathMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/refresh", "/api/auth/login", "/api/auth/register", "/api/auth/refresh").permitAll()
            .pathMatchers(HttpMethod.GET, "/api/v1/orders", "/api/orders").hasRole("ADMIN")
            .pathMatchers(HttpMethod.PUT, "/api/v1/orders/**", "/api/orders/**").hasRole("ADMIN")
            .pathMatchers(HttpMethod.POST, "/api/v1/inventory/**", "/api/inventory/**").hasRole("ADMIN")
            .pathMatchers(HttpMethod.GET, "/api/v1/products/admin", "/api/products/admin").hasRole("ADMIN")
            .pathMatchers(HttpMethod.GET, "/api/v1/products", "/api/products", "/api/v1/products/**", "/api/products/**").permitAll()
            .pathMatchers(HttpMethod.POST, "/api/v1/products/**", "/api/products/**").hasRole("ADMIN")
            .pathMatchers(HttpMethod.PUT, "/api/v1/products/**", "/api/products/**", "/api/v1/inventory/**", "/api/inventory/**").hasRole("ADMIN")
            .pathMatchers(HttpMethod.DELETE, "/api/v1/products/**", "/api/products/**").hasRole("ADMIN")
            .anyExchange().authenticated())
        .exceptionHandling(errors -> errors
            .authenticationEntryPoint((exchange, ex) -> jsonError(exchange, HttpStatus.UNAUTHORIZED, "Authentication required or token invalid"))
            .accessDeniedHandler((exchange, ex) -> jsonError(exchange, HttpStatus.FORBIDDEN, "Insufficient permissions")))
        .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtDecoder(decoder)
            .jwtAuthenticationConverter(new ReactiveJwtAuthenticationConverterAdapter(roles)))
            .authenticationEntryPoint((exchange, ex) -> jsonError(exchange, HttpStatus.UNAUTHORIZED, "Authentication required or token invalid")))
        .build();
  }

  private Mono<Void> jsonError(org.springframework.web.server.ServerWebExchange exchange, HttpStatus status, String message) {
    exchange.getResponse().setStatusCode(status);
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
    var bytes = ("{\"status\":" + status.value() + ",\"error\":\"" + status.getReasonPhrase() + "\",\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
    return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
  }
}
