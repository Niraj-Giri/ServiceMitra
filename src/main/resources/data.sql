-- Insert Dummy Users
INSERT INTO users (id, email, phone_number, password_hash, full_name, role, is_active) VALUES
('u-admin', 'admin@mitra.com', '9999999999', '$2a$10$xyz...', 'System Admin', 'ADMIN', true),
('u-cust1', 'john@example.com', '9876543210', '$2a$10$xyz...', 'John Doe', 'CUSTOMER', true),
('u-prov1', 'rahul@auto.com', '9876543211', '$2a$10$xyz...', 'Rahul Sharma', 'PROVIDER', true),
('u-prov2', 'amit@speedy.com', '9876543212', '$2a$10$xyz...', 'Amit Singh', 'PROVIDER', true);

-- Insert Service Categories
INSERT INTO service_categories (id, name, slug, description, is_active) VALUES
('cat-1', 'Two Wheeler', 'two-wheeler', 'Bike and Scooter repair', true),
('cat-2', 'Four Wheeler', 'four-wheeler', 'Car and SUV repair', true);

-- Insert Service Subcategories
INSERT INTO service_subcategories (id, category_id, name, slug, is_active) VALUES
('subcat-1', 'cat-1', 'Flat Tire', 'flat-tire-2w', true),
('subcat-2', 'cat-2', 'Engine Issue', 'engine-issue-4w', true);

-- Insert Services
INSERT INTO services (id, subcategory_id, name, base_price, estimated_duration_minutes) VALUES
('srv-1', 'subcat-1', 'Puncture Repair', 150.00, 30),
('srv-2', 'subcat-2', 'Jump Start', 300.00, 45);

-- Insert Provider Profiles (with Spatial data)
INSERT INTO provider_profiles (id, user_id, business_name, pan_number, citizenship_number, document_urls, verification_status, geo_location, address_text) VALUES
('p-1', 'u-prov1', 'Rahul Auto Works', 'PAN12345', 'CIT9876', '{}', 'APPROVED', ST_GeomFromText('POINT(28.6139 77.2090)', 4326), 'New Delhi, Delhi'),
('p-2', 'u-prov2', 'Speedy Garage', 'PAN67890', 'CIT5432', '{}', 'APPROVED', ST_GeomFromText('POINT(28.6145 77.2085)', 4326), 'Connaught Place, Delhi');
