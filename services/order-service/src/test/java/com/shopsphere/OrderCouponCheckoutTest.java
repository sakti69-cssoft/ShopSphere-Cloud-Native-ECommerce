package com.shopsphere;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderCouponCheckoutTest {
  private UUID user;
  private UUID addressId;
  private UUID productId;
  private OrderRepository.InMemory orders;
  private CouponStore.InMemory couponStore;
  private CouponService coupons;
  private TrackingInventory inventory;
  private OrderService service;

  @BeforeEach
  void setUp() {
    user = UUID.randomUUID();
    addressId = UUID.randomUUID();
    productId = UUID.randomUUID();
    orders = new OrderRepository.InMemory();
    couponStore = new CouponStore.InMemory();
    coupons = new CouponService(couponStore);
    Instant now = Instant.now();
    couponStore.save(new Coupon(UUID.randomUUID(), "WELCOME10", Coupon.DiscountType.PERCENTAGE,
        new BigDecimal("10"), new BigDecimal("500"), new BigDecimal("500"),
        now.plus(1, ChronoUnit.DAYS), 100, 1, true, now, now));
    inventory = new TrackingInventory();
    service = new OrderService(orders, inventory, new OrderIdempotencyStore.InMemory(),
        item -> new ProductPricingGateway.PricedProduct(item.productId(), "Catalog product", "CAT-SKU",
            new BigDecimal("1200"), true),
        (requestedUser, requestedAddress, authorization) -> {
          assertEquals(user, requestedUser);
          assertEquals(addressId, requestedAddress);
          assertEquals("Bearer test-token", authorization);
          return new Address("Synthetic Customer", "10 Test Avenue", "Flat 2", "Bengaluru", "Karnataka",
              "560001", "India", "9999999999");
        }, coupons);
  }

  @Test
  void quotesAndPersistsServerTotalsCouponAndSavedAddressSnapshot() {
    var items = List.of(new OrderDtos.Item(productId, "Forged", "FAKE", BigDecimal.ONE, 1));
    var quote = service.quote(new CouponDtos.QuoteRequest(user, items, "welcome10"));
    assertEquals(new BigDecimal("1200"), quote.subtotal());
    assertEquals(new BigDecimal("120.00"), quote.discount());
    assertEquals(BigDecimal.ZERO, quote.deliveryFee());
    assertEquals(new BigDecimal("1080.00"), quote.totalAmount());

    Order order = service.create("coupon-checkout", "Bearer test-token",
        new OrderDtos.Create(user, items, BigDecimal.ZERO, BigDecimal.ZERO, addressId, "welcome10", null));
    assertEquals("WELCOME10", order.couponCode());
    assertEquals(new BigDecimal("120.00"), order.discount());
    assertEquals(new BigDecimal("1080.00"), order.totalAmount());
    assertEquals("10 Test Avenue", order.shippingAddress().line1());
    assertEquals("Catalog product", order.items().getFirst().productName());
    assertEquals(1, couponStore.usageCount(couponStore.findByCode("WELCOME10").orElseThrow().id()));
    assertEquals(1, inventory.reserved);
  }

  @Test
  void duplicateSubmissionReturnsSameOrderWithoutDoubleUsageOrInventory() {
    var request = new OrderDtos.Create(user,
        List.of(new OrderDtos.Item(productId, null, null, null, 1)), null, null, addressId, "WELCOME10", null);
    Order first = service.create("same-coupon-order", "Bearer test-token", request);
    Order second = service.create("same-coupon-order", "Bearer test-token", request);
    assertEquals(first.id(), second.id());
    assertEquals(1, orders.findByUser(user).size());
    assertEquals(1, inventory.reserved);
    assertEquals(1, couponStore.usageCount(couponStore.findByCode("WELCOME10").orElseThrow().id()));
  }

  @Test
  void insufficientStockCompensatesInventoryAndDoesNotConsumeCoupon() {
    inventory.failOnSecondReservation = true;
    UUID secondProduct = UUID.randomUUID();
    var request = new OrderDtos.Create(user, List.of(
        new OrderDtos.Item(productId, null, null, null, 1),
        new OrderDtos.Item(secondProduct, null, null, null, 1)),
        null, null, addressId, "WELCOME10", null);
    assertThrows(IllegalStateException.class,
        () -> service.create("insufficient-stock", "Bearer test-token", request));
    assertEquals(0, inventory.reserved);
    assertTrue(orders.findByUser(user).isEmpty());
    assertEquals(0, couponStore.usageCount(couponStore.findByCode("WELCOME10").orElseThrow().id()));
  }

  private static class TrackingInventory implements InventoryGateway {
    int reserved;
    int calls;
    boolean failOnSecondReservation;

    public void reserve(UUID productId, int quantity) {
      calls++;
      if (failOnSecondReservation && calls == 2) throw new IllegalStateException("Insufficient stock");
      reserved += quantity;
    }

    public void release(UUID productId, int quantity) {
      reserved -= quantity;
    }
  }
}
