-- Migration V7: Create Admin Suite Tables and Schema Improvements (Tailored for Nepal context)

CREATE TABLE IF NOT EXISTS platform_settings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    platform_name VARCHAR(100) NOT NULL DEFAULT 'ServiceMitra',
    commission_percentage DECIMAL(5, 2) NOT NULL DEFAULT 10.00,
    support_number VARCHAR(20) NOT NULL DEFAULT '9800000000',
    cancellation_policy TEXT NULL,
    auto_assignment_rules VARCHAR(100) DEFAULT 'CLOSEST_DISTANCE',
    booking_radius DOUBLE DEFAULT 15.0,
    working_hours VARCHAR(100) DEFAULT '08:00-20:00',
    tax_settings VARCHAR(100) DEFAULT 'NONE',
    payment_gateway VARCHAR(50) DEFAULT 'COD'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed default settings if empty
INSERT INTO platform_settings (platform_name, commission_percentage, support_number) 
SELECT 'ServiceMitra', 10.00, '9800000000' FROM DUAL
WHERE NOT EXISTS (SELECT * FROM platform_settings);

CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin VARCHAR(100) NOT NULL,
    action TEXT NOT NULL,
    entity VARCHAR(100) NULL,
    old_value TEXT NULL,
    new_value TEXT NULL,
    timestamp DATETIME NOT NULL,
    ip_address VARCHAR(45) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS complaints (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    subject VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    evidence_url VARCHAR(255) NULL,
    created_at DATETIME NOT NULL,
    resolved_at DATETIME NULL,
    FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE,
    FOREIGN KEY (customer_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS complaint_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    complaint_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    sender_role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    FOREIGN KEY (complaint_id) REFERENCES complaints(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    commission DECIMAL(10, 2) NOT NULL,
    provider_earnings DECIMAL(10, 2) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    transaction_id VARCHAR(100) NOT NULL,
    created_at DATETIME NOT NULL,
    FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS payout_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider_id BIGINT NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL,
    FOREIGN KEY (provider_id) REFERENCES providers(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS booking_status_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    updated_at DATETIME NOT NULL,
    updated_by VARCHAR(50) NOT NULL,
    notes TEXT NULL,
    FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Add verification / documentation columns to providers
ALTER TABLE providers 
ADD COLUMN IF NOT EXISTS bank_details VARCHAR(255) NULL,
ADD COLUMN IF NOT EXISTS certificates_urls TEXT NULL,
ADD COLUMN IF NOT EXISTS acceptance_rate DECIMAL(5, 2) NOT NULL DEFAULT 100.00,
ADD COLUMN IF NOT EXISTS commission_percentage DECIMAL(5, 2) NULL;
