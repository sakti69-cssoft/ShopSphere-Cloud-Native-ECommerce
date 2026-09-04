package com.shopsphere;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

interface CouponStore {
  Optional<Coupon> findByCode(String code);
  Optional<Coupon> findByCodeForUpdate(String code);
  Optional<Coupon> findById(UUID id);
  List<Coupon> findAll();
  Coupon save(Coupon coupon);
  long usageCount(UUID couponId);
  long customerUsageCount(UUID couponId, UUID userId);
  BigDecimal totalDiscount(UUID couponId);
  void recordUsage(CouponUsage usage);

  class InMemory implements CouponStore {
    private final Map<UUID, Coupon> coupons = new ConcurrentHashMap<>();
    private final Map<UUID, CouponUsage> usages = new ConcurrentHashMap<>();

    public Optional<Coupon> findByCode(String code) {
      return coupons.values().stream().filter(coupon -> coupon.code().equalsIgnoreCase(code)).findFirst();
    }

    public synchronized Optional<Coupon> findByCodeForUpdate(String code) {
      return findByCode(code);
    }

    public Optional<Coupon> findById(UUID id) {
      return Optional.ofNullable(coupons.get(id));
    }

    public List<Coupon> findAll() {
      return coupons.values().stream().sorted(Comparator.comparing(Coupon::code)).toList();
    }

    public Coupon save(Coupon coupon) {
      if (coupons.values().stream().anyMatch(existing -> existing.code().equalsIgnoreCase(coupon.code())
          && !existing.id().equals(coupon.id()))) throw new CouponConflictException("Coupon code already exists");
      coupons.put(coupon.id(), coupon);
      return coupon;
    }

    public long usageCount(UUID couponId) {
      return usages.values().stream().filter(usage -> usage.couponId().equals(couponId)).count();
    }

    public long customerUsageCount(UUID couponId, UUID userId) {
      return usages.values().stream().filter(usage -> usage.couponId().equals(couponId)
          && usage.userId().equals(userId)).count();
    }

    public BigDecimal totalDiscount(UUID couponId) {
      return usages.values().stream().filter(usage -> usage.couponId().equals(couponId))
          .map(CouponUsage::discountAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public synchronized void recordUsage(CouponUsage usage) {
      if (usages.values().stream().anyMatch(existing -> existing.orderId().equals(usage.orderId()))) return;
      usages.put(usage.id(), usage);
    }
  }
}
