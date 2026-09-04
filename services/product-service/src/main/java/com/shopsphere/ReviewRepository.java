package com.shopsphere;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public interface ReviewRepository {
  Review save(Review review);
  Optional<Review> findById(UUID id);
  Optional<Review> findByUserAndProduct(UUID userId,UUID productId);
  ProductDtos.Page<Review> findByProduct(UUID productId,int page,int size);
  ReviewDtos.Stats stats(UUID productId);
  void delete(UUID id);

  class InMemory implements ReviewRepository {
    private final Map<UUID,Review> data=new ConcurrentHashMap<>();
    public Review save(Review review) { data.put(review.id(),review); return review; }
    public Optional<Review> findById(UUID id) { return Optional.ofNullable(data.get(id)); }
    public Optional<Review> findByUserAndProduct(UUID userId,UUID productId) { return data.values().stream().filter(review -> review.userId().equals(userId)&&review.productId().equals(productId)).findFirst(); }
    public ProductDtos.Page<Review> findByProduct(UUID productId,int page,int size) { var all=data.values().stream().filter(review -> review.productId().equals(productId)).sorted(Comparator.comparing(Review::createdAt).reversed()).toList(); int start=Math.min(page*size,all.size()); return new ProductDtos.Page<>(all.subList(start,Math.min(start+size,all.size())),page,size,all.size(),(int)Math.ceil(all.size()/(double)size)); }
    public ReviewDtos.Stats stats(UUID productId) { var ratings=data.values().stream().filter(review -> review.productId().equals(productId)).mapToInt(Review::rating).summaryStatistics(); return new ReviewDtos.Stats(ratings.getCount()==0?0:ratings.getAverage(),ratings.getCount()); }
    public void delete(UUID id) { data.remove(id); }
  }
}
