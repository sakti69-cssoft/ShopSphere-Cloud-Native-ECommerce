package com.shopsphere;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders/admin/coupons")
@PreAuthorize("hasRole('ADMIN')")
class CouponAdminController {
  private final CouponService coupons;

  CouponAdminController(CouponService coupons) {
    this.coupons = coupons;
  }

  @GetMapping
  List<CouponDtos.CouponResponse> all() {
    return coupons.all();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  CouponDtos.CouponResponse create(@Valid @RequestBody CouponDtos.AdminRequest request) {
    return coupons.create(request);
  }

  @PutMapping("/{id}")
  CouponDtos.CouponResponse update(@PathVariable UUID id, @Valid @RequestBody CouponDtos.AdminRequest request) {
    return coupons.update(id, request);
  }

  @GetMapping("/{id}/usage")
  CouponDtos.UsageResponse usage(@PathVariable UUID id) {
    return coupons.usage(id);
  }
}
