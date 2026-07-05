-- ============================================================
-- V6: Production-ready schema improvements
-- Adds missing columns, indexes, and supporting tables
-- ============================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- Fix OTP verifications table — add security columns
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE otp_verifications
    MODIFY COLUMN otp VARCHAR(6) NOT NULL,
    MODIFY COLUMN phone VARCHAR(20) NOT NULL;

-- Add attempt_count if not exists
SET @dbname = DATABASE();
SET @tablename = 'otp_verifications';

SET @columnname = 'attempt_count';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    'ALTER TABLE otp_verifications ADD COLUMN attempt_count TINYINT DEFAULT 0'
));
PREPARE alterIfNotExists FROM @preparedStatement; EXECUTE alterIfNotExists; DEALLOCATE PREPARE alterIfNotExists;

SET @columnname = 'is_used';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    'ALTER TABLE otp_verifications ADD COLUMN is_used BOOLEAN DEFAULT FALSE'
));
PREPARE alterIfNotExists FROM @preparedStatement; EXECUTE alterIfNotExists; DEALLOCATE PREPARE alterIfNotExists;

SET @columnname = 'used_at';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    'ALTER TABLE otp_verifications ADD COLUMN used_at DATETIME NULL'
));
PREPARE alterIfNotExists FROM @preparedStatement; EXECUTE alterIfNotExists; DEALLOCATE PREPARE alterIfNotExists;

-- ─────────────────────────────────────────────────────────────────────────────
-- Fix providers table
-- ─────────────────────────────────────────────────────────────────────────────
SET @tablename = 'providers';

SET @columnname = 'total_jobs';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    'ALTER TABLE providers ADD COLUMN total_jobs INT DEFAULT 0'
));
PREPARE alterIfNotExists FROM @preparedStatement; EXECUTE alterIfNotExists; DEALLOCATE PREPARE alterIfNotExists;

SET @columnname = 'admin_notes';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    'ALTER TABLE providers ADD COLUMN admin_notes TEXT'
));
PREPARE alterIfNotExists FROM @preparedStatement; EXECUTE alterIfNotExists; DEALLOCATE PREPARE alterIfNotExists;

-- Change status default to PENDING_REVIEW for new providers
-- (existing records keep their status)
ALTER TABLE providers MODIFY COLUMN status VARCHAR(30) DEFAULT 'PENDING_REVIEW';

-- Change wallet_balance to DECIMAL for precision (was DECIMAL already in V3, just ensuring)
ALTER TABLE providers MODIFY COLUMN wallet_balance DECIMAL(12,2) DEFAULT 0.00;

-- Change rating_cache to store BigDecimal properly
ALTER TABLE providers MODIFY COLUMN rating_cache DECIMAL(3,2) DEFAULT 0.00;

-- ─────────────────────────────────────────────────────────────────────────────
-- Fix bookings table — add new tracking columns
-- ─────────────────────────────────────────────────────────────────────────────
SET @tablename = 'bookings';

SET @columnname = 'otp_generated_at';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    'ALTER TABLE bookings ADD COLUMN otp_generated_at DATETIME NULL'
));
PREPARE alterIfNotExists FROM @preparedStatement; EXECUTE alterIfNotExists; DEALLOCATE PREPARE alterIfNotExists;

SET @columnname = 'started_at';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    'ALTER TABLE bookings ADD COLUMN started_at DATETIME NULL'
));
PREPARE alterIfNotExists FROM @preparedStatement; EXECUTE alterIfNotExists; DEALLOCATE PREPARE alterIfNotExists;

SET @columnname = 'completed_at';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    'ALTER TABLE bookings ADD COLUMN completed_at DATETIME NULL'
));
PREPARE alterIfNotExists FROM @preparedStatement; EXECUTE alterIfNotExists; DEALLOCATE PREPARE alterIfNotExists;

SET @columnname = 'cancelled_at';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    'ALTER TABLE bookings ADD COLUMN cancelled_at DATETIME NULL'
));
PREPARE alterIfNotExists FROM @preparedStatement; EXECUTE alterIfNotExists; DEALLOCATE PREPARE alterIfNotExists;

SET @columnname = 'cancelled_by';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    'ALTER TABLE bookings ADD COLUMN cancelled_by VARCHAR(20) NULL'
));
PREPARE alterIfNotExists FROM @preparedStatement; EXECUTE alterIfNotExists; DEALLOCATE PREPARE alterIfNotExists;

SET @columnname = 'cancellation_reason';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    'ALTER TABLE bookings ADD COLUMN cancellation_reason TEXT NULL'
));
PREPARE alterIfNotExists FROM @preparedStatement; EXECUTE alterIfNotExists; DEALLOCATE PREPARE alterIfNotExists;

SET @columnname = 'rejected_provider_ids';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    'ALTER TABLE bookings ADD COLUMN rejected_provider_ids VARCHAR(500) NULL'
));
PREPARE alterIfNotExists FROM @preparedStatement; EXECUTE alterIfNotExists; DEALLOCATE PREPARE alterIfNotExists;

-- Fix status column to accept new enum values (increase length)
ALTER TABLE bookings MODIFY COLUMN status VARCHAR(30) NOT NULL DEFAULT 'PENDING_DISPATCH';

-- ─────────────────────────────────────────────────────────────────────────────
-- Update existing booking statuses to match new enum naming
-- ─────────────────────────────────────────────────────────────────────────────
UPDATE bookings SET status = 'COMPLETED' WHERE status IN ('completed', 'COMPLETE');
UPDATE bookings SET status = 'CANCELLED_BY_CUSTOMER' WHERE status IN ('cancelled', 'CANCELLED');
UPDATE bookings SET status = 'PENDING_DISPATCH' WHERE status IN ('pending', 'PENDING', 'PENDING_DISPATCH');

-- ─────────────────────────────────────────────────────────────────────────────
-- Add service listing columns if missing
-- ─────────────────────────────────────────────────────────────────────────────
SET @tablename = 'services';

SET @columnname = 'duration_min';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    'ALTER TABLE services ADD COLUMN duration_min INT NULL'
));
PREPARE alterIfNotExists FROM @preparedStatement; EXECUTE alterIfNotExists; DEALLOCATE PREPARE alterIfNotExists;

SET @columnname = 'what_included';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    'ALTER TABLE services ADD COLUMN what_included TEXT NULL'
));
PREPARE alterIfNotExists FROM @preparedStatement; EXECUTE alterIfNotExists; DEALLOCATE PREPARE alterIfNotExists;

SET @columnname = 'what_excluded';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    'ALTER TABLE services ADD COLUMN what_excluded TEXT NULL'
));
PREPARE alterIfNotExists FROM @preparedStatement; EXECUTE alterIfNotExists; DEALLOCATE PREPARE alterIfNotExists;

-- ─────────────────────────────────────────────────────────────────────────────
-- Add multi-dimension rating columns to reviews table
-- ─────────────────────────────────────────────────────────────────────────────
SET @tablename = 'reviews';

SET @columnname = 'punctuality_score';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    'ALTER TABLE reviews ADD COLUMN punctuality_score TINYINT NOT NULL DEFAULT 5'
));
PREPARE alterIfNotExists FROM @preparedStatement; EXECUTE alterIfNotExists; DEALLOCATE PREPARE alterIfNotExists;

SET @columnname = 'quality_score';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    'ALTER TABLE reviews ADD COLUMN quality_score TINYINT NOT NULL DEFAULT 5'
));
PREPARE alterIfNotExists FROM @preparedStatement; EXECUTE alterIfNotExists; DEALLOCATE PREPARE alterIfNotExists;

SET @columnname = 'behavior_score';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    'ALTER TABLE reviews ADD COLUMN behavior_score TINYINT NOT NULL DEFAULT 5'
));
PREPARE alterIfNotExists FROM @preparedStatement; EXECUTE alterIfNotExists; DEALLOCATE PREPARE alterIfNotExists;

SET @columnname = 'overall_score';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    'ALTER TABLE reviews ADD COLUMN overall_score DECIMAL(3,2) NOT NULL DEFAULT 5.00'
));
PREPARE alterIfNotExists FROM @preparedStatement; EXECUTE alterIfNotExists; DEALLOCATE PREPARE alterIfNotExists;

SET @columnname = 'is_visible';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    'ALTER TABLE reviews ADD COLUMN is_visible BOOLEAN DEFAULT TRUE'
));
PREPARE alterIfNotExists FROM @preparedStatement; EXECUTE alterIfNotExists; DEALLOCATE PREPARE alterIfNotExists;

-- Rename 'rating' column to match entity (customer_id is already there)
-- We keep 'rating' as a column, just add the new ones above

-- ─────────────────────────────────────────────────────────────────────────────
-- Performance indexes (idempotent — CREATE INDEX IF NOT EXISTS)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_bookings_provider_id ON bookings(provider_id);
CREATE INDEX IF NOT EXISTS idx_bookings_user_id ON bookings(user_id);
CREATE INDEX IF NOT EXISTS idx_bookings_status ON bookings(status);
CREATE INDEX IF NOT EXISTS idx_bookings_created_at ON bookings(created_at);
CREATE INDEX IF NOT EXISTS idx_bookings_scheduled_at ON bookings(scheduled_at);
CREATE INDEX IF NOT EXISTS idx_providers_status ON providers(status);
CREATE INDEX IF NOT EXISTS idx_providers_category ON providers(service_category);
CREATE INDEX IF NOT EXISTS idx_otp_phone ON otp_verifications(phone);
CREATE INDEX IF NOT EXISTS idx_reviews_provider_id ON reviews(provider_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- Seed realistic data for development and testing
-- ─────────────────────────────────────────────────────────────────────────────
-- Categories (insert only if services table is empty)
INSERT INTO services (category, name, description, base_price, price_type, duration_min, what_included, what_excluded, is_active)
SELECT * FROM (SELECT
    'ELECTRICAL' AS category,
    'Fan Repair' AS name,
    'Ceiling or table fan not working? Our certified electrician will diagnose and repair.' AS description,
    500.00 AS base_price,
    'FIXED' AS price_type,
    60 AS duration_min,
    'Labor charge, Basic inspection, 30-day service warranty' AS what_included,
    'Replacement parts (fan motor, blades), New fan purchase' AS what_excluded,
    TRUE AS is_active
) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM services WHERE name = 'Fan Repair' LIMIT 1);

INSERT INTO services (category, name, description, base_price, price_type, duration_min, what_included, what_excluded, is_active)
SELECT * FROM (SELECT
    'ELECTRICAL' AS category,
    'Electrical Wiring Inspection' AS name,
    'Full home electrical safety inspection and minor fixes.' AS description,
    800.00 AS base_price,
    'FIXED' AS price_type,
    90 AS duration_min,
    'Labor charge, Full inspection, Written report' AS what_included,
    'Cable replacement, New switchboard installation' AS what_excluded,
    TRUE AS is_active
) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM services WHERE name = 'Electrical Wiring Inspection' LIMIT 1);

INSERT INTO services (category, name, description, base_price, price_type, duration_min, what_included, what_excluded, is_active)
SELECT * FROM (SELECT
    'PLUMBING' AS category,
    'Tap Repair / Replacement' AS name,
    'Fix leaking or broken taps quickly.' AS description,
    400.00 AS base_price,
    'FIXED' AS price_type,
    45 AS duration_min,
    'Labor charge, Basic sealant' AS what_included,
    'New tap purchase, Pipe replacement' AS what_excluded,
    TRUE AS is_active
) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM services WHERE name = 'Tap Repair / Replacement' LIMIT 1);

INSERT INTO services (category, name, description, base_price, price_type, duration_min, what_included, what_excluded, is_active)
SELECT * FROM (SELECT
    'PLUMBING' AS category,
    'Drain Cleaning' AS name,
    'Blocked drain? We will clear it fast.' AS description,
    600.00 AS base_price,
    'FIXED' AS price_type,
    60 AS duration_min,
    'Labor, Basic equipment, Cleaning materials' AS what_included,
    'Pipe replacement, Structural repairs' AS what_excluded,
    TRUE AS is_active
) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM services WHERE name = 'Drain Cleaning' LIMIT 1);

INSERT INTO services (category, name, description, base_price, price_type, duration_min, what_included, what_excluded, is_active)
SELECT * FROM (SELECT
    'CLEANING' AS category,
    'Home Deep Cleaning (2BHK)' AS name,
    'Full deep cleaning of a 2BHK apartment including kitchen and bathrooms.' AS description,
    2500.00 AS base_price,
    'FIXED' AS price_type,
    240 AS duration_min,
    'All cleaning materials, 2 experienced cleaners, Kitchen degreasing, Bathroom sanitizing' AS what_included,
    'Sofa/carpet shampooing, Window exterior cleaning, Pest control' AS what_excluded,
    TRUE AS is_active
) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM services WHERE name = 'Home Deep Cleaning (2BHK)' LIMIT 1);

INSERT INTO services (category, name, description, base_price, price_type, duration_min, what_included, what_excluded, is_active)
SELECT * FROM (SELECT
    'AC' AS category,
    'AC Service & Cleaning' AS name,
    'Split AC filter cleaning, coil wash, and performance check.' AS description,
    1500.00 AS base_price,
    'FIXED' AS price_type,
    90 AS duration_min,
    'Filter cleaning, Indoor coil wash, Gas pressure check, Performance test' AS what_included,
    'Gas refill (extra charge), PCB repair, Compressor replacement' AS what_excluded,
    TRUE AS is_active
) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM services WHERE name = 'AC Service & Cleaning' LIMIT 1);

INSERT INTO services (category, name, description, base_price, price_type, duration_min, what_included, what_excluded, is_active)
SELECT * FROM (SELECT
    'PAINTING' AS category,
    'Interior Wall Painting (per room)' AS name,
    'Professional interior painting for one room (approx 150 sq ft).' AS description,
    3500.00 AS base_price,
    'FIXED' AS price_type,
    300 AS duration_min,
    'Labor, Primer coat, 2 paint coats, Drop cloth protection' AS what_included,
    'Paint material cost, Furniture moving, Ceiling painting' AS what_excluded,
    TRUE AS is_active
) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM services WHERE name = 'Interior Wall Painting (per room)' LIMIT 1);
