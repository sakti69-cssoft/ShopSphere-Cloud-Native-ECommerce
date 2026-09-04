package com.shopsphere;

import java.time.Instant;
import java.util.UUID;

public record Review(UUID id,UUID productId,UUID userId,String displayName,int rating,String title,String text,Instant createdAt,Instant updatedAt) {}
