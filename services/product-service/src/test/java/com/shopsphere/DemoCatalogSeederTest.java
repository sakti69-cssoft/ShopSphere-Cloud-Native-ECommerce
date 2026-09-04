package com.shopsphere;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DemoCatalogSeederTest {
  @Test void seedIsAdditiveAndIdempotent() throws Exception { var products=new ProductRepository.InMemory();var seeder=new DemoCatalogSeeder(products);seeder.run(null);seeder.run(null);assertEquals(5,products.findAll().size());assertTrue(products.findBySku("DEMO-CARGO-001").isPresent()); }
}
