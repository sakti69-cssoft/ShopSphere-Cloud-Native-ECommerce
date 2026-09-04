package com.shopsphere;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public interface WishlistRepository {
  WishlistItem save(WishlistItem item);
  Optional<WishlistItem> find(UUID userId, UUID productId);
  List<WishlistItem> findByUser(UUID userId);
  void delete(UUID userId, UUID productId);

  class InMemory implements WishlistRepository {
    private final Map<String, WishlistItem> data = new ConcurrentHashMap<>();
    private static String key(UUID userId, UUID productId) { return userId + ":" + productId; }
    public WishlistItem save(WishlistItem item) { return data.computeIfAbsent(key(item.userId(), item.productId()), ignored -> item); }
    public Optional<WishlistItem> find(UUID userId, UUID productId) { return Optional.ofNullable(data.get(key(userId, productId))); }
    public List<WishlistItem> findByUser(UUID userId) { return data.values().stream().filter(item -> item.userId().equals(userId)).sorted(Comparator.comparing(WishlistItem::createdAt).reversed()).toList(); }
    public void delete(UUID userId, UUID productId) { data.remove(key(userId, productId)); }
  }
}
