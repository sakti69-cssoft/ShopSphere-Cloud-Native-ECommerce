package com.shopsphere;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class CouponDtos {
  private CouponDtos() {}

  record QuoteRequest(
      @NotNull UUID userId,
      @NotNull @Valid @Size(min = 1, max = 100) List<OrderDtos.Item> items,
      @Pattern(regexp = "^[A-Za-z0-9_-]{3,32}$") String couponCode) {}

  record QuoteResponse(
      BigDecimal subtotal,
      BigDecimal discount,
      BigDecimal deliveryFee,
      BigDecimal totalAmount,
      String couponCode,
      String discountDescription) {}

  record AdminRequest(
      @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{3,32}$") String code,
      @NotNull Coupon.DiscountType discountType,
      @NotNull @DecimalMin("0.01") @DecimalMax("1000000") BigDecimal discountValue,
      @NotNull @DecimalMin("0.00") BigDecimal minimumOrderValue,
      @DecimalMin("0.01") BigDecimal maximumDiscount,
      @NotNull Instant expiresAt,
      @Positive Integer usageLimit,
      @Positive Integer perCustomerLimit,
      boolean active) {}

  record CouponResponse(
      UUID id,
      String code,
      Coupon.DiscountType discountType,
      BigDecimal discountValue,
      BigDecimal minimumOrderValue,
      BigDecimal maximumDiscount,
      Instant expiresAt,
      Integer usageLimit,
      Integer perCustomerLimit,
      boolean active,
      long usageCount,
      Instant createdAt,
      Instant updatedAt) {}

  record UsageResponse(UUID couponId, String code, long usageCount, BigDecimal totalDiscount) {}
}
