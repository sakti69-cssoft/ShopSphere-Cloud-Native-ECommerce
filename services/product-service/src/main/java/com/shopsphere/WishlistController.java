package com.shopsphere;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products/wishlist")
public class WishlistController {
  private final WishlistService service;
  WishlistController(WishlistService service) { this.service=service; }
  @GetMapping List<Product> list(@AuthenticationPrincipal Jwt jwt) { return service.list(user(jwt)); }
  @PostMapping("/{productId}") @ResponseStatus(HttpStatus.CREATED) Product add(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID productId) { return service.add(user(jwt),productId); }
  @DeleteMapping("/{productId}") @ResponseStatus(HttpStatus.NO_CONTENT) void remove(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID productId) { service.remove(user(jwt),productId); }
  private UUID user(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
}
