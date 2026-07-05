SET @dbname = DATABASE();
SET @tablename = 'providers';
SET @constraintname = (SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = 'user_id' AND REFERENCED_TABLE_NAME IS NOT NULL LIMIT 1);

-- Drop Foreign Key dynamically
SET @preparedStatement = CONCAT('ALTER TABLE providers DROP FOREIGN KEY ', @constraintname);
PREPARE dropFk FROM @preparedStatement; EXECUTE dropFk; DEALLOCATE PREPARE dropFk;

-- Drop user_id column
ALTER TABLE providers DROP COLUMN user_id;

-- Add Unique constraint on phone
ALTER TABLE providers ADD UNIQUE (phone);
