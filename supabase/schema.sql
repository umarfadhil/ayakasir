-- AyaKasir Supabase Schema (PostgreSQL)
-- Run this in your Supabase SQL editor to set up the remote database

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Users table
CREATE TABLE users (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    pin_hash TEXT NOT NULL,
    pin_salt TEXT NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('OWNER', 'CASHIER')),
    feature_access TEXT,
    is_active BOOLEAN DEFAULT true,
    synced BOOLEAN DEFAULT true,
    updated_at BIGINT NOT NULL,
    created_at BIGINT NOT NULL
);

-- Categories table
CREATE TABLE categories (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    sort_order INTEGER DEFAULT 0,
    category_type TEXT NOT NULL DEFAULT 'MENU',
    synced BOOLEAN DEFAULT true,
    updated_at BIGINT NOT NULL
);

-- Products table
CREATE TABLE products (
    id UUID PRIMARY KEY,
    category_id UUID REFERENCES categories(id) ON DELETE SET NULL,
    name TEXT NOT NULL,
    description TEXT,
    price BIGINT NOT NULL,
    image_path TEXT,
    is_active BOOLEAN DEFAULT true,
    product_type TEXT NOT NULL DEFAULT 'MENU_ITEM',
    synced BOOLEAN DEFAULT true,
    updated_at BIGINT NOT NULL
);

CREATE INDEX idx_products_category ON products(category_id);

-- Variants table
CREATE TABLE variants (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    price_adjustment BIGINT DEFAULT 0,
    synced BOOLEAN DEFAULT true,
    updated_at BIGINT NOT NULL
);

CREATE INDEX idx_variants_product ON variants(product_id);

-- Product components table
CREATE TABLE product_components (
    id UUID PRIMARY KEY,
    parent_product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    component_product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    component_variant_id TEXT NOT NULL DEFAULT '',
    required_qty INTEGER NOT NULL,
    unit TEXT NOT NULL DEFAULT 'pcs',
    sort_order INTEGER DEFAULT 0,
    synced BOOLEAN DEFAULT true,
    updated_at BIGINT NOT NULL
);

CREATE INDEX idx_product_components_parent ON product_components(parent_product_id);
CREATE INDEX idx_product_components_component ON product_components(component_product_id);

-- Vendors table
CREATE TABLE vendors (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    phone TEXT,
    address TEXT,
    synced BOOLEAN DEFAULT true,
    updated_at BIGINT NOT NULL
);

-- Inventory table (composite primary key)
CREATE TABLE inventory (
    product_id UUID NOT NULL,
    variant_id TEXT NOT NULL DEFAULT '', -- empty string for no variant
    current_qty INTEGER DEFAULT 0,
    min_qty INTEGER DEFAULT 0,
    synced BOOLEAN DEFAULT true,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (product_id, variant_id)
);

-- Goods receiving table
CREATE TABLE goods_receiving (
    id UUID PRIMARY KEY,
    vendor_id UUID REFERENCES vendors(id) ON DELETE SET NULL,
    date BIGINT NOT NULL,
    notes TEXT,
    synced BOOLEAN DEFAULT true,
    updated_at BIGINT NOT NULL
);

CREATE INDEX idx_goods_receiving_vendor ON goods_receiving(vendor_id);

-- Goods receiving items table
CREATE TABLE goods_receiving_items (
    id UUID PRIMARY KEY,
    receiving_id UUID NOT NULL REFERENCES goods_receiving(id) ON DELETE CASCADE,
    product_id UUID NOT NULL,
    variant_id TEXT NOT NULL DEFAULT '',
    qty INTEGER NOT NULL,
    cost_per_unit BIGINT NOT NULL,
    unit TEXT NOT NULL DEFAULT 'pcs',
    synced BOOLEAN DEFAULT true,
    updated_at BIGINT NOT NULL
);

CREATE INDEX idx_goods_receiving_items_receiving ON goods_receiving_items(receiving_id);

-- Transactions table
CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    date BIGINT NOT NULL,
    total BIGINT NOT NULL,
    payment_method TEXT NOT NULL CHECK (payment_method IN ('CASH', 'QRIS')),
    status TEXT NOT NULL CHECK (status IN ('COMPLETED', 'VOIDED')),
    synced BOOLEAN DEFAULT true,
    updated_at BIGINT NOT NULL
);

CREATE INDEX idx_transactions_user ON transactions(user_id);
CREATE INDEX idx_transactions_date ON transactions(date);
CREATE INDEX idx_transactions_synced ON transactions(synced);

-- Cash withdrawals table
CREATE TABLE cash_withdrawals (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    amount BIGINT NOT NULL,
    reason TEXT NOT NULL,
    date BIGINT NOT NULL,
    synced BOOLEAN DEFAULT true,
    updated_at BIGINT NOT NULL
);

CREATE INDEX idx_cash_withdrawals_user ON cash_withdrawals(user_id);
CREATE INDEX idx_cash_withdrawals_date ON cash_withdrawals(date);
CREATE INDEX idx_cash_withdrawals_synced ON cash_withdrawals(synced);

-- Transaction items table
CREATE TABLE transaction_items (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    product_id UUID NOT NULL,
    variant_id TEXT NOT NULL DEFAULT '',
    product_name TEXT NOT NULL, -- snapshot
    variant_name TEXT, -- snapshot
    qty INTEGER NOT NULL,
    unit_price BIGINT NOT NULL,
    subtotal BIGINT NOT NULL,
    synced BOOLEAN DEFAULT true,
    updated_at BIGINT NOT NULL
);

CREATE INDEX idx_transaction_items_transaction ON transaction_items(transaction_id);

-- Sync queue table (not needed on server, only for client)
-- Server doesn't need this table as it's the source of truth

-- Row Level Security (RLS) policies
-- For now, disable RLS for simplicity since this is a single-tenant app
-- In production, you'd want to add proper RLS policies

ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE products ENABLE ROW LEVEL SECURITY;
ALTER TABLE variants ENABLE ROW LEVEL SECURITY;
ALTER TABLE product_components ENABLE ROW LEVEL SECURITY;
ALTER TABLE vendors ENABLE ROW LEVEL SECURITY;
ALTER TABLE inventory ENABLE ROW LEVEL SECURITY;
ALTER TABLE goods_receiving ENABLE ROW LEVEL SECURITY;
ALTER TABLE goods_receiving_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE cash_withdrawals ENABLE ROW LEVEL SECURITY;
ALTER TABLE transaction_items ENABLE ROW LEVEL SECURITY;

-- Create permissive policies (allow all operations)
-- WARNING: This is for development only. In production, implement proper auth.

CREATE POLICY "Allow all on users" ON users FOR ALL USING (true);
CREATE POLICY "Allow all on categories" ON categories FOR ALL USING (true);
CREATE POLICY "Allow all on products" ON products FOR ALL USING (true);
CREATE POLICY "Allow all on variants" ON variants FOR ALL USING (true);
CREATE POLICY "Allow all on product_components" ON product_components FOR ALL USING (true);
CREATE POLICY "Allow all on vendors" ON vendors FOR ALL USING (true);
CREATE POLICY "Allow all on inventory" ON inventory FOR ALL USING (true);
CREATE POLICY "Allow all on goods_receiving" ON goods_receiving FOR ALL USING (true);
CREATE POLICY "Allow all on goods_receiving_items" ON goods_receiving_items FOR ALL USING (true);
CREATE POLICY "Allow all on transactions" ON transactions FOR ALL USING (true);
CREATE POLICY "Allow all on cash_withdrawals" ON cash_withdrawals FOR ALL USING (true);
CREATE POLICY "Allow all on transaction_items" ON transaction_items FOR ALL USING (true);

-- Seed default owner (optional - only if you want to seed via SQL)
-- The app will seed this automatically on first launch
-- INSERT INTO users (id, name, pin_hash, pin_salt, role, updated_at, created_at)
-- VALUES (
--     uuid_generate_v4(),
--     'Pemilik',
--     'your_hashed_pin_here',
--     'your_salt_here',
--     'OWNER',
--     EXTRACT(EPOCH FROM NOW()) * 1000,
--     EXTRACT(EPOCH FROM NOW()) * 1000
-- );
