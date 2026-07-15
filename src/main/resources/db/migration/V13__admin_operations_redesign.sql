-- Flyway Migration V13: Admin Operations Redesign

-- Part 2: Provider Strike and Metrics
ALTER TABLE providers ADD COLUMN completion_rate DECIMAL(5,2) DEFAULT 100.00;
ALTER TABLE providers ADD COLUMN cancellation_rate DECIMAL(5,2) DEFAULT 0.00;
ALTER TABLE providers ADD COLUMN response_time_min INT DEFAULT 0;

CREATE TABLE provider_strikes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider_id BIGINT NOT NULL,
    strike_number INT NOT NULL,
    reason TEXT NOT NULL,
    created_by VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NULL,
    internal_notes TEXT,
    FOREIGN KEY (provider_id) REFERENCES providers(id) ON DELETE CASCADE
);

-- Part 3: Customer Trust Flags
ALTER TABLE users ADD COLUMN coupons_disabled BOOLEAN DEFAULT FALSE;
ALTER TABLE users ADD COLUMN rewards_disabled BOOLEAN DEFAULT FALSE;
ALTER TABLE users ADD COLUMN bookings_limited BOOLEAN DEFAULT FALSE;

-- Part 4: Booking Pause & Escalation
ALTER TABLE bookings ADD COLUMN is_paused BOOLEAN DEFAULT FALSE;
ALTER TABLE bookings ADD COLUMN is_escalated BOOLEAN DEFAULT FALSE;

ALTER TABLE task_requests ADD COLUMN is_paused BOOLEAN DEFAULT FALSE;
ALTER TABLE task_requests ADD COLUMN is_escalated BOOLEAN DEFAULT FALSE;

-- Part 5: Complaint Resolution Metrics
ALTER TABLE complaints ADD COLUMN category VARCHAR(50) DEFAULT 'OTHER';
ALTER TABLE complaints ADD COLUMN winner VARCHAR(30) NULL;
ALTER TABLE complaints ADD COLUMN refund_amount DECIMAL(12,2) DEFAULT 0.00;
ALTER TABLE complaints ADD COLUMN penalty_amount DECIMAL(12,2) DEFAULT 0.00;
ALTER TABLE complaints ADD COLUMN compensation_amount DECIMAL(12,2) DEFAULT 0.00;

-- Part 6: Provider Wallet & Payout Ledger
CREATE TABLE provider_wallets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider_id BIGINT UNIQUE NOT NULL,
    balance DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(10) DEFAULT 'NPR',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (provider_id) REFERENCES providers(id) ON DELETE CASCADE
);

CREATE TABLE wallet_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    wallet_id BIGINT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    type VARCHAR(30) NOT NULL, -- CREDIT, DEBIT, REFUND, PENALTY, ADJUSTMENT
    reference_id VARCHAR(100),
    description VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (wallet_id) REFERENCES provider_wallets(id) ON DELETE CASCADE
);

-- Part 7: Refund Parameters
ALTER TABLE transactions ADD COLUMN refund_type VARCHAR(30) NULL;
ALTER TABLE transactions ADD COLUMN refund_destination VARCHAR(30) NULL;

-- Part 8: Review Flagging and Appeals
ALTER TABLE reviews ADD COLUMN is_reported BOOLEAN DEFAULT FALSE;
ALTER TABLE reviews ADD COLUMN report_reason VARCHAR(255) NULL;
ALTER TABLE reviews ADD COLUMN appeal_status VARCHAR(30) NULL; -- PENDING, APPROVED, REJECTED
ALTER TABLE reviews ADD COLUMN moderation_notes TEXT NULL;

-- Part 9: Targeted Notifications
CREATE TABLE broadcast_notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    target_group VARCHAR(50) NOT NULL, -- ALL_USERS, CITY, CATEGORY
    filter_value VARCHAR(100) NULL,
    channel VARCHAR(20) NOT NULL, -- SMS, EMAIL, PUSH
    title VARCHAR(150) NULL,
    content TEXT NOT NULL,
    sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    sent_by VARCHAR(50) NOT NULL
);

-- Part 10: Actionable Audits
ALTER TABLE audit_logs ADD COLUMN reason TEXT NULL;
