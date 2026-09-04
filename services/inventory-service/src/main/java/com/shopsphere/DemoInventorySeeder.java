package com.shopsphere;

import java.util.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="shopsphere.demo-catalog.enabled",havingValue="true")
class DemoInventorySeeder implements ApplicationRunner {
  private final InventoryService inventory;
  DemoInventorySeeder(InventoryService inventory) { this.inventory=inventory; }
  public void run(ApplicationArguments ignored) {
    items().forEach(item -> { try { inventory.get(item.productId()); } catch(NotFoundException missing) { inventory.set(item.productId(),item.sku(),item.quantity(),5); } });
  }
  static List<Seed> items() { return List.of(
      new Seed(UUID.fromString("d1000000-0000-4000-8000-000000000001"),"DEMO-CARGO-001",24),
      new Seed(UUID.fromString("d1000000-0000-4000-8000-000000000002"),"DEMO-RUN-001",18),
      new Seed(UUID.fromString("d1000000-0000-4000-8000-000000000003"),"DEMO-SHIRT-001",30),
      new Seed(UUID.fromString("d1000000-0000-4000-8000-000000000004"),"DEMO-AUDIO-001",16),
      new Seed(UUID.fromString("d1000000-0000-4000-8000-000000000005"),"DEMO-BAG-001",20)); }
  record Seed(UUID productId,String sku,int quantity) {}
}
