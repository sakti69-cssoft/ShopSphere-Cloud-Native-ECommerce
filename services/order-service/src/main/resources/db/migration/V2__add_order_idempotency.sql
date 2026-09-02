CREATE TABLE order_idempotency (
  id BINARY(16) NOT NULL PRIMARY KEY,
  idempotency_key VARCHAR(160) NOT NULL,
  user_id BINARY(16) NOT NULL,
  request_hash CHAR(64) NOT NULL,
  order_id BINARY(16),
  status VARCHAR(24) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT uk_order_idempotency_key UNIQUE (idempotency_key),
  CONSTRAINT fk_order_idempotency_order FOREIGN KEY (order_id) REFERENCES orders(id)
);
CREATE INDEX idx_order_idempotency_user_created ON order_idempotency(user_id, created_at);
