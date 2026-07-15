-- Flyway Migration V14: Align foreign keys to task_requests instead of legacy bookings
SET @dbname = DATABASE();

-- 1. Drop foreign key constraint on booking_id in complaints pointing to bookings table
SET @constraintname1 = (SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'complaints' AND COLUMN_NAME = 'booking_id' AND REFERENCED_TABLE_NAME = 'bookings' LIMIT 1);
SET @preparedStatement1 = IF(@constraintname1 IS NOT NULL, CONCAT('ALTER TABLE complaints DROP FOREIGN KEY ', @constraintname1), 'SELECT 1');
PREPARE dropFk1 FROM @preparedStatement1; EXECUTE dropFk1; DEALLOCATE PREPARE dropFk1;

-- 2. Drop foreign key constraint on booking_id in transactions pointing to bookings table
SET @constraintname2 = (SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'transactions' AND COLUMN_NAME = 'booking_id' AND REFERENCED_TABLE_NAME = 'bookings' LIMIT 1);
SET @preparedStatement2 = IF(@constraintname2 IS NOT NULL, CONCAT('ALTER TABLE transactions DROP FOREIGN KEY ', @constraintname2), 'SELECT 1');
PREPARE dropFk2 FROM @preparedStatement2; EXECUTE dropFk2; DEALLOCATE PREPARE dropFk2;

-- 3. Drop foreign key constraint on booking_id in booking_status_history pointing to bookings table
SET @constraintname3 = (SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'booking_status_history' AND COLUMN_NAME = 'booking_id' AND REFERENCED_TABLE_NAME = 'bookings' LIMIT 1);
SET @preparedStatement3 = IF(@constraintname3 IS NOT NULL, CONCAT('ALTER TABLE booking_status_history DROP FOREIGN KEY ', @constraintname3), 'SELECT 1');
PREPARE dropFk3 FROM @preparedStatement3; EXECUTE dropFk3; DEALLOCATE PREPARE dropFk3;
