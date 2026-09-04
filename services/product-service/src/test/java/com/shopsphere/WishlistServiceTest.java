package com.shopsphere;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;

class WishlistServiceTest {
  ProductRepository.InMemory products;
  WishlistRepository.InMemory wishlist;
  WishlistService service;
  Product product;
  @BeforeEach void setup() { products=new ProductRepository.InMemory();wishlist=new WishlistRepository.InMemory();service=new WishlistService(wishlist,products);product=products.save(new Product(UUID.randomUUID(),"SKU-1","Cargo Pants","cargo-pants","Demo","UrbanTrail","Fashion",BigDecimal.valueOf(2499),BigDecimal.valueOf(3299),BigDecimal.valueOf(24),0,0,List.of("/image.jpg"),Map.of(),true,Instant.now(),Instant.now())); }
  @Test void addIsPersistentAndDuplicateSafe() { UUID user=UUID.randomUUID();service.add(user,product.id());service.add(user,product.id());assertEquals(1,service.list(user).size()); }
  @Test void usersSeeOnlyTheirOwnWishlistAndCanRemove() { UUID first=UUID.randomUUID(),second=UUID.randomUUID();service.add(first,product.id());assertTrue(service.list(second).isEmpty());service.remove(first,product.id());assertTrue(service.list(first).isEmpty()); }
}
