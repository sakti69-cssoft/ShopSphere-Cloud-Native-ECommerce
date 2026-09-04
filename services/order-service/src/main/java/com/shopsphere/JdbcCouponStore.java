package com.shopsphere;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCouponStore implements CouponStore {
  private static final String SELECT = "SELECT BIN_TO_UUID(id) id,code,discount_type,discount_value,minimum_order_value,maximum_discount,expires_at,usage_limit,per_customer_limit,active,created_at,updated_at FROM coupons";
  private final JdbcTemplate jdbc;

  JdbcCouponStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<Coupon> findByCode(String code) {
    return jdbc.query(SELECT + " WHERE code=?", this::map, code).stream().findFirst();
  }

  public Optional<Coupon> findByCodeForUpdate(String code) {
    return jdbc.query(SELECT + " WHERE code=? FOR UPDATE", this::map, code).stream().findFirst();
  }

  public Optional<Coupon> findById(UUID id) {
    return jdbc.query(SELECT + " WHERE id=UUID_TO_BIN(?)", this::map, id.toString()).stream().findFirst();
  }

  public List<Coupon> findAll() {
    return jdbc.query(SELECT + " ORDER BY code", this::map);
  }

  public Coupon save(Coupon coupon) {
    Object[] values = {coupon.code(), coupon.discountType().name(), coupon.discountValue(),
        coupon.minimumOrderValue(), coupon.maximumDiscount(), Timestamp.from(coupon.expiresAt()),
        coupon.usageLimit(), coupon.perCustomerLimit(), coupon.active(), Timestamp.from(coupon.updatedAt()),
        coupon.id().toString()};
    int updated = jdbc.update("UPDATE coupons SET code=?,discount_type=?,discount_value=?,minimum_order_value=?,maximum_discount=?,expires_at=?,usage_limit=?,per_customer_limit=?,active=?,updated_at=? WHERE id=UUID_TO_BIN(?)", values);
    if (updated == 0) {
      try {
        jdbc.update("INSERT INTO coupons(id,code,discount_type,discount_value,minimum_order_value,maximum_discount,expires_at,usage_limit,per_customer_limit,active,created_at,updated_at) VALUES(UUID_TO_BIN(?),?,?,?,?,?,?,?,?,?,?,?)",
            coupon.id().toString(), coupon.code(), coupon.discountType().name(), coupon.discountValue(),
            coupon.minimumOrderValue(), coupon.maximumDiscount(), Timestamp.from(coupon.expiresAt()),
            coupon.usageLimit(), coupon.perCustomerLimit(), coupon.active(), Timestamp.from(coupon.createdAt()),
            Timestamp.from(coupon.updatedAt()));
      } catch (DuplicateKeyException duplicate) {
        throw new CouponConflictException("Coupon code already exists");
      }
    }
    return findById(coupon.id()).orElseThrow();
  }

  public long usageCount(UUID couponId) {
    Long count = jdbc.queryForObject("SELECT COUNT(*) FROM coupon_usages WHERE coupon_id=UUID_TO_BIN(?)",
        Long.class, couponId.toString());
    return count == null ? 0 : count;
  }

  public long customerUsageCount(UUID couponId, UUID userId) {
    Long count = jdbc.queryForObject("SELECT COUNT(*) FROM coupon_usages WHERE coupon_id=UUID_TO_BIN(?) AND user_id=UUID_TO_BIN(?)",
        Long.class, couponId.toString(), userId.toString());
    return count == null ? 0 : count;
  }

  public BigDecimal totalDiscount(UUID couponId) {
    BigDecimal total = jdbc.queryForObject("SELECT COALESCE(SUM(discount_amount),0) FROM coupon_usages WHERE coupon_id=UUID_TO_BIN(?)",
        BigDecimal.class, couponId.toString());
    return total == null ? BigDecimal.ZERO : total;
  }

  public void recordUsage(CouponUsage usage) {
    try {
      jdbc.update("INSERT INTO coupon_usages(id,coupon_id,user_id,order_id,discount_amount,created_at) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),?,?)",
          usage.id().toString(), usage.couponId().toString(), usage.userId().toString(),
          usage.orderId().toString(), usage.discountAmount(), Timestamp.from(usage.createdAt()));
    } catch (DuplicateKeyException duplicate) {
      throw new CouponConflictException("Coupon usage was already recorded for this order");
    }
  }

  private Coupon map(ResultSet result, int row) throws SQLException {
    BigDecimal maximum = result.getBigDecimal("maximum_discount");
    Integer usageLimit = result.getObject("usage_limit", Integer.class);
    Integer customerLimit = result.getObject("per_customer_limit", Integer.class);
    return new Coupon(UUID.fromString(result.getString("id")), result.getString("code"),
        Coupon.DiscountType.valueOf(result.getString("discount_type")), result.getBigDecimal("discount_value"),
        result.getBigDecimal("minimum_order_value"), maximum, result.getTimestamp("expires_at").toInstant(),
        usageLimit, customerLimit, result.getBoolean("active"), result.getTimestamp("created_at").toInstant(),
        result.getTimestamp("updated_at").toInstant());
  }
}
