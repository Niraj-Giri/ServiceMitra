-- Add image_url to services table
ALTER TABLE services ADD COLUMN IF NOT EXISTS image_url VARCHAR(255) NULL;
