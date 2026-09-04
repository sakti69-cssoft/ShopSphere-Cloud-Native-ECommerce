package com.shopsphere;

import java.time.Instant;
import java.util.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Document("wishlists")
@CompoundIndex(name = "uk_wishlist_user_product", def = "{'userId':1,'productId':1}", unique = true)
class WishlistDocument {
  @Id UUID id;
  @Indexed UUID userId;
  UUID productId;
  Instant createdAt;
  WishlistDocument() {}
  WishlistDocument(WishlistItem item) { id=item.id(); userId=item.userId(); productId=item.productId(); createdAt=item.createdAt(); }
  WishlistItem domain() { return new WishlistItem(id,userId,productId,createdAt); }
}

@Repository
class MongoWishlistRepository implements WishlistRepository {
  private final MongoTemplate mongo;
  MongoWishlistRepository(MongoTemplate mongo) { this.mongo = mongo; }
  public WishlistItem save(WishlistItem item) { return mongo.save(new WishlistDocument(item)).domain(); }
  public Optional<WishlistItem> find(UUID userId, UUID productId) { return Optional.ofNullable(mongo.findOne(Query.query(Criteria.where("userId").is(userId).and("productId").is(productId)),WishlistDocument.class)).map(WishlistDocument::domain); }
  public List<WishlistItem> findByUser(UUID userId) { return mongo.find(Query.query(Criteria.where("userId").is(userId)).with(Sort.by(Sort.Direction.DESC,"createdAt")),WishlistDocument.class).stream().map(WishlistDocument::domain).toList(); }
  public void delete(UUID userId, UUID productId) { mongo.remove(Query.query(Criteria.where("userId").is(userId).and("productId").is(productId)),WishlistDocument.class); }
}
