-- Apply explicitly before enabling. No existing tables are modified.
CREATE TABLE IF NOT EXISTS extension_hpay_order (
  order_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  user_id BIGINT NOT NULL,
  package_id BIGINT NOT NULL,
  product_name VARCHAR(255) NOT NULL,
  credits DECIMAL(18,4) NOT NULL,
  amount_usdt DECIMAL(12,2) NOT NULL,
  merchant_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'pending',
  trade_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  checkout_url TEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  checked_at DATETIME NULL,
  paid_at DATETIME NULL,
  PRIMARY KEY (order_no),
  UNIQUE KEY uq_hpay_trade (merchant_id,trade_no),
  KEY ix_hpay_user (user_id,created_at),
  KEY ix_hpay_pending (status,checked_at,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
