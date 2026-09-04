package com.shopsphere;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Order(
    UUID id,
    String orderNumber,
    UUID userId,
    List<OrderItem> items,
    BigDecimal subtotal,
    BigDecimal discount,
    String couponCode,
    BigDecimal deliveryFee,
    BigDecimal totalAmount,
    Status status,
    PaymentStatus paymentStatus,
    Address shippingAddress,
    Instant createdAt,
    Instant updatedAt) {
  enum Status { PENDING, CONFIRMED, PROCESSING, SHIPPED, DELIVERED, CANCELLED }
  enum PaymentStatus { PENDING, SUCCESS, FAILED, REFUNDED }
}

record OrderItem(UUID productId, String productName, String sku, BigDecimal unitPrice, int quantity,
    BigDecimal lineTotal) {}

record Address(String recipient, String line1, String line2, String city, String state, String postalCode,
    String country, String phone) {}
