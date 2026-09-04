package com.shopsphere;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CouponServiceTest {
  private CouponStore.InMemory store;
  private CouponService service;
  private UUID user;

  @BeforeEach
  void setUp() {
    store = new CouponStore.InMemory();
    service = new CouponService(store);
    user = UUID.randomUUID();
  }

  @Test
  void calculatesPercentageAndFixedDiscountsOnTheServer() {
    save("TENOFF", Coupon.DiscountType.PERCENTAGE, "10", "500", "75", true,
        Instant.now().plus(1, ChronoUnit.DAYS), 10, 2);
    save("SAVE100", Coupon.DiscountType.FIXED, "100", "500", null, true,
        Instant.now().plus(1, ChronoUnit.DAYS), 10, 2);
    assertEquals(new BigDecimal("75.00"), service.quote(user, "tenoff", new BigDecimal("1000")).amount());
    assertEquals(new BigDecimal("100.00"), service.quote(user, "SAVE100", new BigDecimal("1000")).amount());
  }

  @Test
  void rejectsInvalidExpiredInactiveAndBelowMinimumCoupons() {
    save("EXPIRED", Coupon.DiscountType.PERCENTAGE, "10", "0", null, true,
        Instant.now().minus(1, ChronoUnit.DAYS), 10, 1);
    save("INACTIVE", Coupon.DiscountType.PERCENTAGE, "10", "0", null, false,
        Instant.now().plus(1, ChronoUnit.DAYS), 10, 1);
    save("MINIMUM", Coupon.DiscountType.FIXED, "50", "1000", null, true,
        Instant.now().plus(1, ChronoUnit.DAYS), 10, 1);
    assertThrows(CouponException.class, () -> service.quote(user, "UNKNOWN", BigDecimal.TEN));
    assertThrows(CouponException.class, () -> service.quote(user, "EXPIRED", BigDecimal.TEN));
    assertThrows(CouponException.class, () -> service.quote(user, "INACTIVE", BigDecimal.TEN));
    assertThrows(CouponException.class, () -> service.quote(user, "MINIMUM", new BigDecimal("999")));
  }

  @Test
  void capsDiscountAtSubtotalSoTotalsCannotBecomeNegative() {
    save("GIANT", Coupon.DiscountType.FIXED, "1000", "0", null, true,
        Instant.now().plus(1, ChronoUnit.DAYS), 10, 1);
    assertEquals(new BigDecimal("100.00"), service.quote(user, "GIANT", new BigDecimal("100")).amount());
  }

  @Test
  void recordsUsageAndEnforcesCustomerLimit() {
    Coupon coupon = save("ONCE", Coupon.DiscountType.FIXED, "50", "0", null, true,
        Instant.now().plus(1, ChronoUnit.DAYS), 10, 1);
    service.redeem(user, UUID.randomUUID(), "ONCE", new BigDecimal("500"), new BigDecimal("50.00"));
    assertEquals(1, store.usageCount(coupon.id()));
    assertThrows(CouponException.class, () -> service.quote(user, "ONCE", new BigDecimal("500")));
  }

  @Test
  void validatesAdminDefinitionsAndUniqueCodes() {
    var request = new CouponDtos.AdminRequest("newdeal", Coupon.DiscountType.PERCENTAGE,
        new BigDecimal("15"), new BigDecimal("250"), new BigDecimal("100"),
        Instant.now().plus(2, ChronoUnit.DAYS), 100, 1, true);
    assertEquals("NEWDEAL", service.create(request).code());
    assertThrows(CouponConflictException.class, () -> service.create(request));
    var invalid = new CouponDtos.AdminRequest("baddeal", Coupon.DiscountType.PERCENTAGE,
        new BigDecimal("101"), BigDecimal.ZERO, null, Instant.now().plus(2, ChronoUnit.DAYS),
        null, null, true);
    assertThrows(CouponException.class, () -> service.create(invalid));
  }

  private Coupon save(String code, Coupon.DiscountType type, String value, String minimum, String maximum,
      boolean active, Instant expiry, Integer usageLimit, Integer customerLimit) {
    Instant now = Instant.now();
    return store.save(new Coupon(UUID.randomUUID(), code, type, new BigDecimal(value), new BigDecimal(minimum),
        maximum == null ? null : new BigDecimal(maximum), expiry, usageLimit, customerLimit, active, now, now));
  }
}
