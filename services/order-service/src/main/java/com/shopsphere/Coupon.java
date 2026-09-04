package com.shopsphere;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

record Coupon(
    UUID id,
    String code,
    DiscountType discountType,
    BigDecimal discountValue,
    BigDecimal minimumOrderValue,
    BigDecimal maximumDiscount,
    Instant expiresAt,
    Integer usageLimit,
    Integer perCustomerLimit,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {
  enum DiscountType { PERCENTAGE, FIXED }
}

record CouponUsage(
    UUID id,
    UUID couponId,
    UUID userId,
    UUID orderId,
    BigDecimal discountAmount,
    Instant createdAt) {}
