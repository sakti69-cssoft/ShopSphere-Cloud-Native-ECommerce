ALTER TABLE orders ADD COLUMN coupon_code VARCHAR(32) NULL AFTER discount;
CREATE INDEX idx_orders_coupon_code ON orders(coupon_code);

CREATE TABLE coupons (
  id BINARY(16) NOT NULL PRIMARY KEY,
  code VARCHAR(32) NOT NULL,
  discount_type VARCHAR(20) NOT NULL,
  discount_value DECIMAL(19,2) NOT NULL,
  minimum_order_value DECIMAL(19,2) NOT NULL DEFAULT 0,
  maximum_discount DECIMAL(19,2),
  expires_at TIMESTAMP(6) NOT NULL,
  usage_limit INT,
  per_customer_limit INT,
  active BOOLEAN NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT uk_coupons_code UNIQUE(code)
);
CREATE INDEX idx_coupons_active_expiry ON coupons(active, expires_at);

CREATE TABLE coupon_usages (
  id BINARY(16) NOT NULL PRIMARY KEY,
  coupon_id BINARY(16) NOT NULL,
  user_id BINARY(16) NOT NULL,
  order_id BINARY(16) NOT NULL,
  discount_amount DECIMAL(19,2) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_coupon_usages_coupon FOREIGN KEY(coupon_id) REFERENCES coupons(id),
  CONSTRAINT fk_coupon_usages_order FOREIGN KEY(order_id) REFERENCES orders(id),
  CONSTRAINT uk_coupon_usages_order UNIQUE(order_id)
);
CREATE INDEX idx_coupon_usages_coupon_customer ON coupon_usages(coupon_id, user_id, created_at);

INSERT INTO coupons(id,code,discount_type,discount_value,minimum_order_value,maximum_discount,expires_at,usage_limit,per_customer_limit,active,created_at,updated_at) VALUES
  (UUID_TO_BIN('10000000-0000-0000-0000-000000000001'),'WELCOME10','PERCENTAGE',10.00,500.00,500.00,'2030-12-31 23:59:59.000000',10000,1,TRUE,NOW(6),NOW(6)),
  (UUID_TO_BIN('10000000-0000-0000-0000-000000000002'),'SAVE100','FIXED',100.00,1000.00,NULL,'2030-12-31 23:59:59.000000',10000,3,TRUE,NOW(6),NOW(6)),
  (UUID_TO_BIN('10000000-0000-0000-0000-000000000003'),'EXPIRED20','PERCENTAGE',20.00,0.00,500.00,'2025-12-31 23:59:59.000000',10000,1,TRUE,'2025-01-01 00:00:00.000000','2025-01-01 00:00:00.000000'),
  (UUID_TO_BIN('10000000-0000-0000-0000-000000000004'),'PAUSED15','PERCENTAGE',15.00,0.00,500.00,'2030-12-31 23:59:59.000000',10000,1,FALSE,NOW(6),NOW(6));
