package com.shopsphere;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ReviewService {
  private final ReviewRepository reviews;
  private final ProductService products;
  ReviewService(ReviewRepository reviews,ProductService products) { this.reviews=reviews;this.products=products; }
  public ProductDtos.Page<Review> list(UUID productId,int page,int size) { products.get(productId); return reviews.findByProduct(productId,Math.max(0,page),Math.max(1,Math.min(size,50))); }
  public Review create(UUID userId,String displayName,UUID productId,ReviewDtos.Request request) { validate(request); products.get(productId); if(reviews.findByUserAndProduct(userId,productId).isPresent())throw new ConflictException("You have already reviewed this product"); Instant now=Instant.now(); Review saved=reviews.save(new Review(UUID.randomUUID(),productId,userId,cleanName(displayName),request.rating(),cleanTitle(request.title()),request.text().trim(),now,now)); updateAggregate(productId); return saved; }
  public Review update(UUID userId,UUID productId,UUID reviewId,ReviewDtos.Request request) { validate(request); Review current=owned(userId,productId,reviewId); Review saved=reviews.save(new Review(current.id(),current.productId(),current.userId(),current.displayName(),request.rating(),cleanTitle(request.title()),request.text().trim(),current.createdAt(),Instant.now())); updateAggregate(productId); return saved; }
  public void delete(UUID userId,UUID productId,UUID reviewId) { owned(userId,productId,reviewId); reviews.delete(reviewId); updateAggregate(productId); }
  private Review owned(UUID userId,UUID productId,UUID reviewId) { Review review=reviews.findById(reviewId).filter(item -> item.productId().equals(productId)).orElseThrow(() -> new NotFoundException("Review not found")); if(!review.userId().equals(userId))throw new ForbiddenException("You can modify only your own review"); return review; }
  private void updateAggregate(UUID productId) { ReviewDtos.Stats stats=reviews.stats(productId); products.updateRating(productId,stats.average(),stats.count()); }
  private void validate(ReviewDtos.Request request) { if(request.rating()<1||request.rating()>5)throw new BadRequestException("Rating must be between 1 and 5"); if(request.text()==null||request.text().trim().isEmpty())throw new BadRequestException("Review text is required"); }
  private String cleanName(String name) { String value=name==null?"ShopSphere customer":name.trim(); return value.isBlank()?"ShopSphere customer":value.substring(0,Math.min(value.length(),80)); }
  private String cleanTitle(String title) { return title==null?"":title.trim(); }
}
