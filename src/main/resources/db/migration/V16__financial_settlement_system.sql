-- V16: Alter tables for financial settlement system

-- 1. Alter task_requests table
ALTER TABLE task_requests ADD COLUMN payment_status VARCHAR(20) DEFAULT 'UNPAID';
ALTER TABLE task_requests ADD COLUMN payment_received_by VARCHAR(20) DEFAULT 'PLATFORM';

-- 2. Alter transactions table
ALTER TABLE transactions ADD COLUMN commission_status VARCHAR(20) DEFAULT 'PENDING';
ALTER TABLE transactions ADD COLUMN commission_due_date TIMESTAMP NULL;
ALTER TABLE transactions ADD COLUMN settlement_status VARCHAR(20) DEFAULT 'PENDING';

-- 3. Alter provider_wallets table
ALTER TABLE provider_wallets ADD COLUMN online_earnings DECIMAL(10,2) DEFAULT 0.00;
ALTER TABLE provider_wallets ADD COLUMN cod_earnings DECIMAL(10,2) DEFAULT 0.00;
ALTER TABLE provider_wallets ADD COLUMN outstanding_commission DECIMAL(10,2) DEFAULT 0.00;
