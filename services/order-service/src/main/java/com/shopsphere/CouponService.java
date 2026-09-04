package com.shopsphere;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class CouponService {
  record Discount(Coupon coupon, BigDecimal amount, String description) {
    static Discount none() { return new Discount(null, BigDecimal.ZERO, null); }
    String code() { return coupon == null ? null : coupon.code(); }
  }

  private final CouponStore store;

  CouponService(CouponStore store) {
    this.store = store;
  }

  Discount quote(UUID userId, String rawCode, BigDecimal subtotal) {
    if (rawCode == null || rawCode.isBlank()) return Discount.none();
    Coupon coupon = store.findByCode(code(rawCode))
        .orElseThrow(() -> new CouponException("Coupon code is invalid"));
    return validate(coupon, userId, subtotal, Instant.now());
  }

  void redeem(UUID userId, UUID orderId, String rawCode, BigDecimal subtotal, BigDecimal expectedDiscount) {
    if (rawCode == null || rawCode.isBlank()) return;
    Coupon coupon = store.findByCodeForUpdate(code(rawCode))
        .orElseThrow(() -> new CouponException("Coupon code is invalid"));
    Discount current = validate(coupon, userId, subtotal, Instant.now());
    if (current.amount().compareTo(expectedDiscount) != 0) {
      throw new CouponException("Coupon eligibility changed; please review the order total");
    }
    store.recordUsage(new CouponUsage(UUID.randomUUID(), coupon.id(), userId, orderId, current.amount(), Instant.now()));
  }

  List<CouponDtos.CouponResponse> all() {
    return store.findAll().stream().map(this::response).toList();
  }

  CouponDtos.CouponResponse create(CouponDtos.AdminRequest request) {
    validateDefinition(request);
    Instant now = Instant.now();
    Coupon coupon = new Coupon(UUID.randomUUID(), code(request.code()), request.discountType(),
        money(request.discountValue()), money(request.minimumOrderValue()), moneyNullable(request.maximumDiscount()),
        request.expiresAt(), request.usageLimit(), request.perCustomerLimit(), request.active(), now, now);
    return response(store.save(coupon));
  }

  CouponDtos.CouponResponse update(UUID id, CouponDtos.AdminRequest request) {
    validateDefinition(request);
    Coupon current = store.findById(id).orElseThrow(() -> new NotFoundException("Coupon not found"));
    Coupon updated = new Coupon(current.id(), code(request.code()), request.discountType(),
        money(request.discountValue()), money(request.minimumOrderValue()), moneyNullable(request.maximumDiscount()),
        request.expiresAt(), request.usageLimit(), request.perCustomerLimit(), request.active(),
        current.createdAt(), Instant.now());
    return response(store.save(updated));
  }

  CouponDtos.UsageResponse usage(UUID id) {
    Coupon coupon = store.findById(id).orElseThrow(() -> new NotFoundException("Coupon not found"));
    return new CouponDtos.UsageResponse(id, coupon.code(), store.usageCount(id), store.totalDiscount(id));
  }

  private Discount validate(Coupon coupon, UUID userId, BigDecimal subtotal, Instant now) {
    if (!coupon.active()) throw new CouponException("Coupon is inactive");
    if (!coupon.expiresAt().isAfter(now)) throw new CouponException("Coupon has expired");
    if (subtotal.compareTo(coupon.minimumOrderValue()) < 0) {
      throw new CouponException("Minimum order value for this coupon is ₹" + coupon.minimumOrderValue().toPlainString());
    }
    if (coupon.usageLimit() != null && store.usageCount(coupon.id()) >= coupon.usageLimit()) {
      throw new CouponException("Coupon usage limit has been reached");
    }
    if (coupon.perCustomerLimit() != null
        && store.customerUsageCount(coupon.id(), userId) >= coupon.perCustomerLimit()) {
      throw new CouponException("You have already used this coupon");
    }
    BigDecimal discount = coupon.discountType() == Coupon.DiscountType.PERCENTAGE
        ? subtotal.multiply(coupon.discountValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
        : coupon.discountValue();
    if (coupon.maximumDiscount() != null) discount = discount.min(coupon.maximumDiscount());
    discount = money(discount.min(subtotal).max(BigDecimal.ZERO));
    String description = coupon.discountType() == Coupon.DiscountType.PERCENTAGE
        ? coupon.discountValue().stripTrailingZeros().toPlainString() + "% off"
        : "₹" + coupon.discountValue().stripTrailingZeros().toPlainString() + " off";
    return new Discount(coupon, discount, description);
  }

  private CouponDtos.CouponResponse response(Coupon coupon) {
    return new CouponDtos.CouponResponse(coupon.id(), coupon.code(), coupon.discountType(), coupon.discountValue(),
        coupon.minimumOrderValue(), coupon.maximumDiscount(), coupon.expiresAt(), coupon.usageLimit(),
        coupon.perCustomerLimit(), coupon.active(), store.usageCount(coupon.id()), coupon.createdAt(), coupon.updatedAt());
  }

  private void validateDefinition(CouponDtos.AdminRequest request) {
    if (request.discountType() == Coupon.DiscountType.PERCENTAGE
        && request.discountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
      throw new CouponException("Percentage discount cannot exceed 100");
    }
  }

  private String code(String raw) {
    return raw.trim().toUpperCase(Locale.ROOT);
  }

  private BigDecimal money(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }

  private BigDecimal moneyNullable(BigDecimal value) {
    return value == null ? null : money(value);
  }
}
