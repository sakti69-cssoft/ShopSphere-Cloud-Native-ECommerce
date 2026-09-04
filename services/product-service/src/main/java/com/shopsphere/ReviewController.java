package com.shopsphere;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products/{productId}/reviews")
public class ReviewController {
  private final ReviewService service;
  ReviewController(ReviewService service) { this.service=service; }
  @GetMapping ProductDtos.Page<Review> list(@PathVariable UUID productId,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="10")int size) { return service.list(productId,page,size); }
  @PostMapping @ResponseStatus(HttpStatus.CREATED) Review create(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID productId,@Valid @RequestBody ReviewDtos.Request request) { return service.create(user(jwt),displayName(jwt),productId,request); }
  @PutMapping("/{reviewId}") Review update(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID productId,@PathVariable UUID reviewId,@Valid @RequestBody ReviewDtos.Request request) { return service.update(user(jwt),productId,reviewId,request); }
  @DeleteMapping("/{reviewId}") @ResponseStatus(HttpStatus.NO_CONTENT) void delete(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID productId,@PathVariable UUID reviewId) { service.delete(user(jwt),productId,reviewId); }
  private UUID user(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
  private String displayName(Jwt jwt) { String email=jwt.getClaimAsString("email"); if(email==null||email.isBlank())return "ShopSphere customer"; int at=email.indexOf('@'); return at>0?email.substring(0,at):email; }
}
