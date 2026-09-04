package com.shopsphere;

import java.time.Instant;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class WishlistService {
  private final WishlistRepository wishlist;
  private final ProductRepository products;
  WishlistService(WishlistRepository wishlist, ProductRepository products) { this.wishlist=wishlist; this.products=products; }

  public List<Product> list(UUID userId) {
    List<WishlistItem> items=wishlist.findByUser(userId);
    Map<UUID,Product> found=new HashMap<>();
    products.findAllById(items.stream().map(WishlistItem::productId).toList()).forEach(product -> found.put(product.id(),product));
    return items.stream().map(item -> found.get(item.productId())).filter(Objects::nonNull).filter(Product::active).toList();
  }

  public Product add(UUID userId, UUID productId) {
    Product product=products.findById(productId).filter(Product::active).orElseThrow(() -> new NotFoundException("Product not found"));
    if(wishlist.find(userId,productId).isEmpty()) {
      try { wishlist.save(new WishlistItem(UUID.randomUUID(),userId,productId,Instant.now())); }
      catch(DuplicateKeyException ignored) { /* A concurrent add resolved to the existing unique row. */ }
    }
    return product;
  }

  public void remove(UUID userId, UUID productId) { wishlist.delete(userId,productId); }
}
