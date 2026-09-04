package com.shopsphere;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class OrderPersistenceContainerTest {
  @Container static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

  @Test
  void migrationsCreateCouponAndOrderSnapshotSchema() throws Exception {
    Flyway.configure().dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
        .locations("classpath:db/migration").load().migrate();
    try (var connection = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        var statement = connection.createStatement()) {
      try (var coupons = statement.executeQuery("SELECT COUNT(*) FROM coupons")) {
        coupons.next();
        assertThat(coupons.getInt(1)).isEqualTo(4);
      }
      try (var columns = statement.executeQuery(
          "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='orders' AND column_name='coupon_code'")) {
        columns.next();
        assertThat(columns.getInt(1)).isEqualTo(1);
      }
      try (var indexes = statement.executeQuery(
          "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='coupon_usages' AND index_name='idx_coupon_usages_coupon_customer'")) {
        indexes.next();
        assertThat(indexes.getInt(1)).isGreaterThan(0);
      }
    }
  }
}
