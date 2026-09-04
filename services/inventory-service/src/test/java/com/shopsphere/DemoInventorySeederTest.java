package com.shopsphere;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DemoInventorySeederTest {
  @Test void seedIsAdditiveAndIdempotent() throws Exception { var repository=new InventoryRepository.InMemory();var seeder=new DemoInventorySeeder(new InventoryService(repository));seeder.run(null);seeder.run(null);for(var item:DemoInventorySeeder.items())assertEquals(item.quantity(),repository.find(item.productId()).orElseThrow().quantityAvailable()); }
}
