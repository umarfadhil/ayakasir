-- Migration Script: Supabase Schema v8 -> v10
-- Run this if you have an existing Supabase database with the old schema
-- This script aligns your Supabase database with App DB version 10

-- ==========================================
-- Step 1: Create restaurants table
-- ==========================================
CREATE TABLE IF NOT EXISTS restaurants (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    owner_email TEXT NOT NULL,
    owner_phone TEXT NOT NULL,
    is_active BOOLEAN DEFAULT true,
    sync_status TEXT NOT NULL DEFAULT 'SYNCED',
    updated_at BIGINT NOT NULL,
    created_at BIGINT NOT NULL
);

-- Enable RLS for restaurants
ALTER TABLE restaurants ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Allow all on restaurants" ON restaurants FOR ALL USING (true);

-- ==========================================
-- Step 2: Migrate users table (synced BOOLEAN -> sync_status TEXT, add email/phone)
-- ==========================================

-- Add new columns if they don't exist
ALTER TABLE users ADD COLUMN IF NOT EXISTS email TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS sync_status TEXT;

-- Migrate data from synced to sync_status
UPDATE users
SET sync_status = CASE
    WHEN synced = true THEN 'SYNCED'
    ELSE 'PENDING'
END
WHERE sync_status IS NULL;

-- Set default for sync_status
ALTER TABLE users ALTER COLUMN sync_status SET DEFAULT 'SYNCED';
ALTER TABLE users ALTER COLUMN sync_status SET NOT NULL;

-- Drop old synced column
ALTER TABLE users DROP COLUMN IF EXISTS synced;

-- Add restaurant_id FK (v10)
ALTER TABLE users ADD COLUMN IF NOT EXISTS restaurant_id UUID REFERENCES restaurants(id);

-- Add feature_access column if missing
ALTER TABLE users ADD COLUMN IF NOT EXISTS feature_access TEXT;

-- ==========================================
-- Step 3: Migrate all other tables (synced -> sync_status)
-- ==========================================

-- Categories
ALTER TABLE categories ADD COLUMN IF NOT EXISTS sync_status TEXT;
UPDATE categories SET sync_status = CASE WHEN synced = true THEN 'SYNCED' ELSE 'PENDING' END WHERE sync_status IS NULL;
ALTER TABLE categories ALTER COLUMN sync_status SET DEFAULT 'SYNCED';
ALTER TABLE categories ALTER COLUMN sync_status SET NOT NULL;
ALTER TABLE categories DROP COLUMN IF EXISTS synced;

-- Products
ALTER TABLE products ADD COLUMN IF NOT EXISTS sync_status TEXT;
UPDATE products SET sync_status = CASE WHEN synced = true THEN 'SYNCED' ELSE 'PENDING' END WHERE sync_status IS NULL;
ALTER TABLE products ALTER COLUMN sync_status SET DEFAULT 'SYNCED';
ALTER TABLE products ALTER COLUMN sync_status SET NOT NULL;
ALTER TABLE products DROP COLUMN IF EXISTS synced;

-- Variants
ALTER TABLE variants ADD COLUMN IF NOT EXISTS sync_status TEXT;
UPDATE variants SET sync_status = CASE WHEN synced = true THEN 'SYNCED' ELSE 'PENDING' END WHERE sync_status IS NULL;
ALTER TABLE variants ALTER COLUMN sync_status SET DEFAULT 'SYNCED';
ALTER TABLE variants ALTER COLUMN sync_status SET NOT NULL;
ALTER TABLE variants DROP COLUMN IF EXISTS synced;

-- Product Components
ALTER TABLE product_components ADD COLUMN IF NOT EXISTS sync_status TEXT;
UPDATE product_components SET sync_status = CASE WHEN synced = true THEN 'SYNCED' ELSE 'PENDING' END WHERE sync_status IS NULL;
ALTER TABLE product_components ALTER COLUMN sync_status SET DEFAULT 'SYNCED';
ALTER TABLE product_components ALTER COLUMN sync_status SET NOT NULL;
ALTER TABLE product_components DROP COLUMN IF EXISTS synced;

-- Vendors
ALTER TABLE vendors ADD COLUMN IF NOT EXISTS sync_status TEXT;
UPDATE vendors SET sync_status = CASE WHEN synced = true THEN 'SYNCED' ELSE 'PENDING' END WHERE sync_status IS NULL;
ALTER TABLE vendors ALTER COLUMN sync_status SET DEFAULT 'SYNCED';
ALTER TABLE vendors ALTER COLUMN sync_status SET NOT NULL;
ALTER TABLE vendors DROP COLUMN IF EXISTS synced;

-- Inventory
ALTER TABLE inventory ADD COLUMN IF NOT EXISTS sync_status TEXT;
UPDATE inventory SET sync_status = CASE WHEN synced = true THEN 'SYNCED' ELSE 'PENDING' END WHERE sync_status IS NULL;
ALTER TABLE inventory ALTER COLUMN sync_status SET DEFAULT 'SYNCED';
ALTER TABLE inventory ALTER COLUMN sync_status SET NOT NULL;
ALTER TABLE inventory DROP COLUMN IF EXISTS synced;

-- Goods Receiving
ALTER TABLE goods_receiving ADD COLUMN IF NOT EXISTS sync_status TEXT;
UPDATE goods_receiving SET sync_status = CASE WHEN synced = true THEN 'SYNCED' ELSE 'PENDING' END WHERE sync_status IS NULL;
ALTER TABLE goods_receiving ALTER COLUMN sync_status SET DEFAULT 'SYNCED';
ALTER TABLE goods_receiving ALTER COLUMN sync_status SET NOT NULL;
ALTER TABLE goods_receiving DROP COLUMN IF EXISTS synced;

-- Goods Receiving Items
ALTER TABLE goods_receiving_items ADD COLUMN IF NOT EXISTS sync_status TEXT;
UPDATE goods_receiving_items SET sync_status = CASE WHEN synced = true THEN 'SYNCED' ELSE 'PENDING' END WHERE sync_status IS NULL;
ALTER TABLE goods_receiving_items ALTER COLUMN sync_status SET DEFAULT 'SYNCED';
ALTER TABLE goods_receiving_items ALTER COLUMN sync_status SET NOT NULL;
ALTER TABLE goods_receiving_items DROP COLUMN IF EXISTS synced;

-- Transactions
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS sync_status TEXT;
UPDATE transactions SET sync_status = CASE WHEN synced = true THEN 'SYNCED' ELSE 'PENDING' END WHERE sync_status IS NULL;
ALTER TABLE transactions ALTER COLUMN sync_status SET DEFAULT 'SYNCED';
ALTER TABLE transactions ALTER COLUMN sync_status SET NOT NULL;
ALTER TABLE transactions DROP COLUMN IF EXISTS synced;

-- Transaction Items
ALTER TABLE transaction_items ADD COLUMN IF NOT EXISTS sync_status TEXT;
UPDATE transaction_items SET sync_status = CASE WHEN synced = true THEN 'SYNCED' ELSE 'PENDING' END WHERE sync_status IS NULL;
ALTER TABLE transaction_items ALTER COLUMN sync_status SET DEFAULT 'SYNCED';
ALTER TABLE transaction_items ALTER COLUMN sync_status SET NOT NULL;
ALTER TABLE transaction_items DROP COLUMN IF EXISTS synced;

-- Cash Withdrawals
ALTER TABLE cash_withdrawals ADD COLUMN IF NOT EXISTS sync_status TEXT;
UPDATE cash_withdrawals SET sync_status = CASE WHEN synced = true THEN 'SYNCED' ELSE 'PENDING' END WHERE sync_status IS NULL;
ALTER TABLE cash_withdrawals ALTER COLUMN sync_status SET DEFAULT 'SYNCED';
ALTER TABLE cash_withdrawals ALTER COLUMN sync_status SET NOT NULL;
ALTER TABLE cash_withdrawals DROP COLUMN IF EXISTS synced;

-- ==========================================
-- Migration Complete
-- ==========================================
-- Your Supabase database is now aligned with App DB v10
