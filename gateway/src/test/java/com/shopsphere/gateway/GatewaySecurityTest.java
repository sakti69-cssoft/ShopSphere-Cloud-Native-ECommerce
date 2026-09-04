package com.shopsphere.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

@WebFluxTest(controllers = FallbackController.class)
@Import({GatewaySecurity.class, GatewayConfiguration.class})
@TestPropertySource(properties = {"shopsphere.jwt.secret=test-secret-that-is-at-least-thirty-two-characters", "shopsphere.cors.allowed-origins=http://localhost:3000"})
class GatewaySecurityTest {
  @Autowired WebTestClient client;

  @Test void protectedRouteRejectsMissingToken() {
    client.get().uri("/api/v1/cart/00000000-0000-0000-0000-000000000000").exchange().expectStatus().isUnauthorized()
        .expectBody().jsonPath("$.status").isEqualTo(401);
  }

  @Test void wishlistAndReviewWritesRejectMissingToken() {
    client.get().uri("/api/v1/products/wishlist").exchange().expectStatus().isUnauthorized();
    client.post().uri("/api/v1/products/00000000-0000-0000-0000-000000000000/reviews").exchange().expectStatus().isUnauthorized();
  }

  @Test void profileAddressAndCouponQuoteRejectMissingToken() {
    client.get().uri("/api/v1/auth/me").exchange().expectStatus().isUnauthorized();
    client.get().uri("/api/v1/auth/addresses").exchange().expectStatus().isUnauthorized();
    client.post().uri("/api/v1/orders/quote").exchange().expectStatus().isUnauthorized();
  }

  @Test void customerCannotUseAdminCouponEndpoints() {
    client.mutateWith(mockJwt().jwt(jwt -> jwt.subject("00000000-0000-0000-0000-000000000000")
        .claim("role", "CUSTOMER"))).get().uri("/api/v1/orders/admin/coupons").exchange()
        .expectStatus().isForbidden();
  }

  @Test void malformedJwtGetsSafeUnauthorizedResponse() {
    client.get().uri("/api/v1/orders/00000000-0000-0000-0000-000000000000")
        .header(HttpHeaders.AUTHORIZATION, "Bearer malformed.token").exchange().expectStatus().isUnauthorized()
        .expectBody().jsonPath("$.message").isEqualTo("Authentication required or token invalid");
  }

  @Test void corsAllowsConfiguredOriginAndPreflight() {
    client.options().uri("/api/v1/auth/login").header(HttpHeaders.ORIGIN, "http://localhost:3000")
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST").header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type").exchange().expectStatus().isNoContent()
        .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000")
        .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
  }

  @Test void fallbackIsSafeAndDoesNotLeakDetails() {
    client.get().uri("/fallback/products").exchange().expectStatus().isEqualTo(503)
        .expectBody().jsonPath("$.message").isEqualTo("Product catalog is temporarily unavailable");
  }
}
