CREATE TABLE coupons (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    discount_type VARCHAR(20) NOT NULL, -- FLAT, PERCENTAGE
    discount_value DECIMAL(12,2) NOT NULL,
    max_discount DECIMAL(12,2),
    min_booking_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    start_date DATETIME NOT NULL,
    expiry_date DATETIME NOT NULL,
    usage_limit INT NOT NULL DEFAULT 0,
    usage_per_customer INT NOT NULL DEFAULT 1,
    applicable_category VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE coupon_usages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    coupon_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    task_request_id BIGINT,
    booking_id BIGINT,
    discount_amount DECIMAL(12,2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (coupon_id) REFERENCES coupons(id) ON DELETE CASCADE
);

ALTER TABLE task_requests ADD COLUMN coupon_code VARCHAR(50);
ALTER TABLE task_requests ADD COLUMN coupon_discount_npr DECIMAL(12,2) DEFAULT 0.00;
ALTER TABLE task_requests ADD COLUMN payment_method VARCHAR(50);
