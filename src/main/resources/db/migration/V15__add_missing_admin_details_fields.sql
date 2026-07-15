-- Add missing fields to providers
ALTER TABLE providers ADD COLUMN gender VARCHAR(20) NULL;
ALTER TABLE providers ADD COLUMN date_of_birth DATE NULL;
ALTER TABLE providers ADD COLUMN city VARCHAR(100) NULL;
ALTER TABLE providers ADD COLUMN state VARCHAR(100) NULL;
ALTER TABLE providers ADD COLUMN pincode VARCHAR(20) NULL;
ALTER TABLE providers ADD COLUMN last_active TIMESTAMP NULL;
ALTER TABLE providers ADD COLUMN emergency_contact VARCHAR(50) NULL;
ALTER TABLE providers ADD COLUMN pan_number VARCHAR(50) NULL;
ALTER TABLE providers ADD COLUMN aadhaar_number VARCHAR(50) NULL;
ALTER TABLE providers ADD COLUMN driving_license VARCHAR(50) NULL;

-- Add missing fields to users
ALTER TABLE users ADD COLUMN gender VARCHAR(20) NULL;
ALTER TABLE users ADD COLUMN date_of_birth DATE NULL;
ALTER TABLE users ADD COLUMN last_login TIMESTAMP NULL;
