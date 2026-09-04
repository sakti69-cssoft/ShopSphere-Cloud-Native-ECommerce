package com.shopsphere;

import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class ProductConfig {
  @Bean JwtDecoder decoder(@Value("${shopsphere.jwt.secret}") String secret) {
    var key=new SecretKeySpec(Arrays.copyOf(secret.getBytes(StandardCharsets.UTF_8),32),"HmacSHA256");
    var decoder=NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer("shopsphere-auth"),jwt -> "access".equals(jwt.getClaimAsString("type"))?OAuth2TokenValidatorResult.success():OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"))));
    return decoder;
  }

  @Bean SecurityFilterChain security(HttpSecurity http,JwtDecoder decoder) throws Exception {
    var roles=new JwtAuthenticationConverter();
    roles.setJwtGrantedAuthoritiesConverter(jwt -> List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_"+jwt.getClaimAsString("role"))));
    return http.csrf(csrf -> csrf.disable()).sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health","/v3/api-docs/**","/swagger-ui/**","/swagger-ui.html").permitAll()
            .requestMatchers(HttpMethod.GET,"/api/products/wishlist").authenticated()
            .requestMatchers(HttpMethod.POST,"/api/products/wishlist/**","/api/products/*/reviews").authenticated()
            .requestMatchers(HttpMethod.PUT,"/api/products/*/reviews/*").authenticated()
            .requestMatchers(HttpMethod.DELETE,"/api/products/wishlist/**","/api/products/*/reviews/*").authenticated()
            .requestMatchers(HttpMethod.GET,"/api/products","/api/products/**").permitAll()
            .anyRequest().hasRole("ADMIN"))
        .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(roles))).build();
  }
}
