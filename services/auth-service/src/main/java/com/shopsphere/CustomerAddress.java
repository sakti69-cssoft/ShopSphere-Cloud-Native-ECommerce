package com.shopsphere;

import java.time.Instant;
import java.util.UUID;

public record CustomerAddress(
    UUID id,
    UUID userId,
    String recipientName,
    String phone,
    String line1,
    String line2,
    String city,
    String state,
    String postalCode,
    String country,
    boolean defaultAddress,
    Instant createdAt,
    Instant updatedAt) {}
