package com.shopsphere;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="shopsphere.demo-catalog.enabled",havingValue="true")
class DemoCatalogSeeder implements ApplicationRunner {
  private final ProductRepository products;
  DemoCatalogSeeder(ProductRepository products) { this.products=products; }
  public void run(ApplicationArguments ignored) {
    demoProducts().forEach(product -> { if(products.findBySku(product.sku()).isEmpty())products.save(product); });
  }
  static List<Product> demoProducts() {
    Instant now=Instant.parse("2026-09-01T00:00:00Z");
    return List.of(
        product("d1000000-0000-4000-8000-000000000001","DEMO-CARGO-001","Classic Utility Cargo Pants","classic-utility-cargo-pants","Comfort-fit cotton cargo pants with six practical pockets and a durable everyday finish.","UrbanTrail","Fashion","2499","3299","/media/product-shirt.jpg",Map.of("Material","Cotton twill","Fit","Regular","Care","Machine wash"),now),
        product("d1000000-0000-4000-8000-000000000002","DEMO-RUN-001","Velocity Running Shoes","velocity-running-shoes","Lightweight cushioned running shoes designed for daily road runs and active commutes.","StrideLab","Fashion","3999","4999","/media/cat-fashion.jpg",Map.of("Upper","Engineered mesh","Sole","Cushioned rubber","Use","Road running"),now),
        product("d1000000-0000-4000-8000-000000000003","DEMO-SHIRT-001","Everyday Oxford Casual Shirt","everyday-oxford-casual-shirt","Breathable long-sleeve Oxford shirt with a clean tailored silhouette for work or weekends.","NorthRow","Fashion","1899","2499","/media/product-shirt.jpg",Map.of("Material","Cotton","Fit","Regular","Sleeve","Full"),now),
        product("d1000000-0000-4000-8000-000000000004","DEMO-AUDIO-001","Pulse Wireless Headphones","pulse-wireless-headphones","Over-ear Bluetooth headphones with balanced sound, soft memory-foam cushions and long battery life.","Auralis","Electronics","5499","6999","/media/product-headphones.jpg",Map.of("Connectivity","Bluetooth 5.3","Battery","40 hours","Charging","USB-C"),now),
        product("d1000000-0000-4000-8000-000000000005","DEMO-BAG-001","Metro Commuter Backpack","metro-commuter-backpack","Water-resistant commuter backpack with a padded laptop sleeve and organized everyday storage.","UrbanTrail","Accessories","2999","3999","/media/cat-accessories.jpg",Map.of("Capacity","24 L","Laptop sleeve","Up to 15.6 inch","Material","Water-resistant polyester"),now));
  }
  private static Product product(String id,String sku,String name,String slug,String description,String brand,String category,String price,String originalPrice,String image,Map<String,String>specs,Instant now) {
    BigDecimal current=new BigDecimal(price),original=new BigDecimal(originalPrice);
    BigDecimal discount=original.subtract(current).multiply(BigDecimal.valueOf(100)).divide(original,2,java.math.RoundingMode.HALF_UP);
    return new Product(UUID.fromString(id),sku,name,slug,description,brand,category,current,original,discount,0,0,List.of(image),specs,true,now,now);
  }
}
