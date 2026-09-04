package com.shopsphere;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;

class ReviewServiceTest {
  ProductService products;
  ReviewRepository.InMemory reviews;
  ReviewService service;
  Product product;
  UUID user;
  @BeforeEach void setup() { products=new ProductService(new ProductRepository.InMemory());product=products.create(new ProductDtos.Request("SKU-1","Cargo Pants","cargo-pants","Demo","UrbanTrail","Fashion",BigDecimal.valueOf(2499),BigDecimal.valueOf(3299),BigDecimal.valueOf(24),0,0,List.of("/image.jpg"),Map.of(),true));reviews=new ReviewRepository.InMemory();service=new ReviewService(reviews,products);user=UUID.randomUUID(); }
  @Test void createsOneReviewAndUpdatesAverage() { Review review=service.create(user,"Aarav",product.id(),new ReviewDtos.Request(5,"Excellent","Comfortable and well made."));assertEquals(5,products.get(product.id()).rating());assertEquals(1,products.get(product.id()).reviewCount());assertThrows(ConflictException.class,()->service.create(user,"Aarav",product.id(),new ReviewDtos.Request(4,"Good","Still a good product.")));assertEquals(review.id(),service.list(product.id(),0,10).content().getFirst().id()); }
  @Test void rejectsInvalidRating() { assertThrows(BadRequestException.class,()->service.create(user,"Aarav",product.id(),new ReviewDtos.Request(6,"Invalid","This must fail."))); }
  @Test void updateDeleteRequireOwnershipAndRecalculateAverage() { Review mine=service.create(user,"Aarav",product.id(),new ReviewDtos.Request(3,"Okay","An average first impression."));UUID other=UUID.randomUUID();assertThrows(ForbiddenException.class,()->service.update(other,product.id(),mine.id(),new ReviewDtos.Request(5,"Nope","Not the review owner.")));service.update(user,product.id(),mine.id(),new ReviewDtos.Request(4,"Better","Updated after more use."));assertEquals(4,products.get(product.id()).rating());assertThrows(ForbiddenException.class,()->service.delete(other,product.id(),mine.id()));service.delete(user,product.id(),mine.id());assertEquals(0,products.get(product.id()).reviewCount());assertEquals(0,products.get(product.id()).rating()); }
}
