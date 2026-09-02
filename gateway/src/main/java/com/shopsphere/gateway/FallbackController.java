package com.shopsphere.gateway;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class FallbackController {
  @GetMapping("/fallback/products")
  ResponseEntity<Map<String, Object>> products() {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
        "timestamp", Instant.now().toString(), "status", 503, "error", "Service Unavailable",
        "message", "Product catalog is temporarily unavailable"));
  }
}
