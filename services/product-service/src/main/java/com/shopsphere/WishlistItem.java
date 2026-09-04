package com.shopsphere;

import java.time.Instant;
import java.util.UUID;

public record WishlistItem(UUID id, UUID userId, UUID productId, Instant createdAt) {}
