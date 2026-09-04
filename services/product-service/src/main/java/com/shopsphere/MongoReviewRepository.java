package com.shopsphere;

import java.time.Instant;
import java.util.*;
import org.bson.Document;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@org.springframework.data.mongodb.core.mapping.Document("reviews")
@CompoundIndex(name="uk_review_user_product",def="{'userId':1,'productId':1}",unique=true)
@CompoundIndex(name="ix_review_product_created",def="{'productId':1,'createdAt':-1}")
class ReviewDocument {
  @Id UUID id;
  UUID productId;
  UUID userId;
  String displayName;
  int rating;
  String title;
  String text;
  Instant createdAt;
  Instant updatedAt;
  ReviewDocument() {}
  ReviewDocument(Review review) { id=review.id();productId=review.productId();userId=review.userId();displayName=review.displayName();rating=review.rating();title=review.title();text=review.text();createdAt=review.createdAt();updatedAt=review.updatedAt(); }
  Review domain() { return new Review(id,productId,userId,displayName,rating,title,text,createdAt,updatedAt); }
}

@Repository
class MongoReviewRepository implements ReviewRepository {
  private final MongoTemplate mongo;
  MongoReviewRepository(MongoTemplate mongo) { this.mongo=mongo; }
  public Review save(Review review) { return mongo.save(new ReviewDocument(review)).domain(); }
  public Optional<Review> findById(UUID id) { return Optional.ofNullable(mongo.findById(id,ReviewDocument.class)).map(ReviewDocument::domain); }
  public Optional<Review> findByUserAndProduct(UUID userId,UUID productId) { return Optional.ofNullable(mongo.findOne(Query.query(Criteria.where("userId").is(userId).and("productId").is(productId)),ReviewDocument.class)).map(ReviewDocument::domain); }
  public ProductDtos.Page<Review> findByProduct(UUID productId,int page,int size) { Query query=Query.query(Criteria.where("productId").is(productId)); long total=mongo.count(query,ReviewDocument.class); query.with(Sort.by(Sort.Direction.DESC,"createdAt")).skip((long)page*size).limit(size); return new ProductDtos.Page<>(mongo.find(query,ReviewDocument.class).stream().map(ReviewDocument::domain).toList(),page,size,total,(int)Math.ceil(total/(double)size)); }
  public ReviewDtos.Stats stats(UUID productId) { Aggregation aggregation=Aggregation.newAggregation(Aggregation.match(Criteria.where("productId").is(productId)),Aggregation.group().avg("rating").as("average").count().as("count")); Document result=mongo.aggregate(aggregation,"reviews",Document.class).getUniqueMappedResult(); return result==null?new ReviewDtos.Stats(0,0):new ReviewDtos.Stats(((Number)result.get("average")).doubleValue(),((Number)result.get("count")).longValue()); }
  public void delete(UUID id) { mongo.remove(Query.query(Criteria.where("_id").is(id)),ReviewDocument.class); }
}
