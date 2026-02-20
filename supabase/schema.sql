-- AyaKasir Supabase Schema (PostgreSQL)
-- Run this in your Supabase SQL editor to set up the remote database
-- Version: Aligned with App DB v15

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Restaurants table (NEW in v9, UPDATED in v15: added qris_image_url, qris_merchant_name)
CREATE TABLE restaurants (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    owner_email TEXT NOT NULL,
    owner_phone TEXT NOT NULL,
    is_active BOOLEAN DEFAULT true,
    qris_image_url TEXT,
    qris_merchant_name TEXT,
    sync_status TEXT NOT NULL DEFAULT 'SYNCED',
    updated_at BIGINT NOT NULL,
    created_at BIGINT NOT NULL
);

-- Storage bucket for QRIS images (run once via Supabase dashboard → Storage → New bucket)
-- Bucket name: qris-images, Public: true
-- Policy: allow INSERT for authenticated users, SELECT for public
-- ALTER TABLE for existing deployments:
-- ALTER TABLE restaurants ADD COLUMN IF NOT EXISTS qris_image_url TEXT;
-- ALTER TABLE restaurants ADD COLUMN IF NOT EXISTS qris_merchant_name TEXT;

-- Users table (UPDATED in v10: added restaurant_id FK, v13: added password_hash/salt)
CREATE TABLE users (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    email TEXT,
    phone TEXT,
    pin_hash TEXT NOT NULL,
    pin_salt TEXT NOT NULL,
    password_hash TEXT,
    password_salt TEXT,
    role TEXT NOT NULL CHECK (role IN ('OWNER', 'CASHIER')),
    restaurant_id UUID REFERENCES restaurants(id),
    feature_access TEXT,
    is_active BOOLEAN DEFAULT true,
    sync_status TEXT NOT NULL DEFAULT 'SYNCED',
    updated_at BIGINT NOT NULL,
    created_at BIGINT NOT NULL
);

-- Categories table
CREATE TABLE categories (
    id UUID PRIMARY KEY,
    restaurant_id UUID REFERENCES restaurants(id),
    name TEXT NOT NULL,
    sort_order INTEGER DEFAULT 0,
    category_type TEXT NOT NULL DEFAULT 'MENU',
    sync_status TEXT NOT NULL DEFAULT 'SYNCED',
    updated_at BIGINT NOT NULL
);

CREATE INDEX idx_categories_restaurant ON categories(restaurant_id);

-- Products table
CREATE TABLE products (
    id UUID PRIMARY KEY,
    restaurant_id UUID REFERENCES restaurants(id),
    category_id UUID REFERENCES categories(id) ON DELETE SET NULL,
    name TEXT NOT NULL,
    description TEXT,
    price BIGINT NOT NULL,
    image_path TEXT,
    is_active BOOLEAN DEFAULT true,
    product_type TEXT NOT NULL DEFAULT 'MENU_ITEM',
    sync_status TEXT NOT NULL DEFAULT 'SYNCED',
    updated_at BIGINT NOT NULL
);

CREATE INDEX idx_products_restaurant ON products(restaurant_id);
CREATE INDEX idx_products_category ON products(category_id);

-- Variants table
CREATE TABLE variants (
    id UUID PRIMARY KEY,
    restaurant_id UUID REFERENCES restaurants(id),
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    price_adjustment BIGINT DEFAULT 0,
    sync_status TEXT NOT NULL DEFAULT 'SYNCED',
    updated_at BIGINT NOT NULL
);

CREATE INDEX idx_variants_restaurant ON variants(restaurant_id);
CREATE INDEX idx_variants_product ON variants(product_id);

-- Product components table
CREATE TABLE product_components (
    id UUID PRIMARY KEY,
    restaurant_id UUID REFERENCES restaurants(id),
    parent_product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    component_product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    component_variant_id TEXT NOT NULL DEFAULT '',
    required_qty INTEGER NOT NULL,
    unit TEXT NOT NULL DEFAULT 'pcs',
    sort_order INTEGER DEFAULT 0,
    sync_status TEXT NOT NULL DEFAULT 'SYNCED',
    updated_at BIGINT NOT NULL
);

CREATE INDEX idx_product_components_restaurant ON product_components(restaurant_id);
CREATE INDEX idx_product_components_parent ON product_components(parent_product_id);
CREATE INDEX idx_product_components_component ON product_components(component_product_id);

-- Vendors table
CREATE TABLE vendors (
    id UUID PRIMARY KEY,
    restaurant_id UUID REFERENCES restaurants(id),
    name TEXT NOT NULL,
    phone TEXT,
    address TEXT,
    sync_status TEXT NOT NULL DEFAULT 'SYNCED',
    updated_at BIGINT NOT NULL
);

CREATE INDEX idx_vendors_restaurant ON vendors(restaurant_id);

-- Inventory table (composite primary key)
CREATE TABLE inventory (
    product_id UUID NOT NULL,
    variant_id TEXT NOT NULL DEFAULT '', -- empty string for no variant
    restaurant_id UUID REFERENCES restaurants(id),
    current_qty INTEGER DEFAULT 0,
    min_qty INTEGER DEFAULT 0,
    unit TEXT NOT NULL DEFAULT 'pcs', -- base unit: g, mL, pcs
    sync_status TEXT NOT NULL DEFAULT 'SYNCED',
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (product_id, variant_id)
);

CREATE INDEX idx_inventory_restaurant ON inventory(restaurant_id);

-- Goods receiving table
CREATE TABLE goods_receiving (
    id UUID PRIMARY KEY,
    restaurant_id UUID REFERENCES restaurants(id),
    vendor_id UUID REFERENCES vendors(id) ON DELETE SET NULL,
    date BIGINT NOT NULL,
    notes TEXT,
    sync_status TEXT NOT NULL DEFAULT 'SYNCED',
    updated_at BIGINT NOT NULL
);

CREATE INDEX idx_goods_receiving_restaurant ON goods_receiving(restaurant_id);
CREATE INDEX idx_goods_receiving_vendor ON goods_receiving(vendor_id);

-- Goods receiving items table
CREATE TABLE goods_receiving_items (
    id UUID PRIMARY KEY,
    restaurant_id UUID REFERENCES restaurants(id),
    receiving_id UUID NOT NULL REFERENCES goods_receiving(id) ON DELETE CASCADE,
    product_id UUID NOT NULL,
    variant_id TEXT NOT NULL DEFAULT '',
    qty INTEGER NOT NULL,
    cost_per_unit BIGINT NOT NULL,
    unit TEXT NOT NULL DEFAULT 'pcs',
    sync_status TEXT NOT NULL DEFAULT 'SYNCED',
    updated_at BIGINT NOT NULL
);

CREATE INDEX idx_goods_receiving_items_restaurant ON goods_receiving_items(restaurant_id);
CREATE INDEX idx_goods_receiving_items_receiving ON goods_receiving_items(receiving_id);

-- Transactions table
CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    restaurant_id UUID REFERENCES restaurants(id),
    user_id UUID NOT NULL,
    date BIGINT NOT NULL,
    total BIGINT NOT NULL,
    payment_method TEXT NOT NULL CHECK (payment_method IN ('CASH', 'QRIS')),
    status TEXT NOT NULL CHECK (status IN ('COMPLETED', 'VOIDED')),
    sync_status TEXT NOT NULL DEFAULT 'SYNCED',
    updated_at BIGINT NOT NULL
);

CREATE INDEX idx_transactions_restaurant ON transactions(restaurant_id);
CREATE INDEX idx_transactions_user ON transactions(user_id);
CREATE INDEX idx_transactions_date ON transactions(date);
CREATE INDEX idx_transactions_sync_status ON transactions(sync_status);

-- Cash withdrawals table
CREATE TABLE cash_withdrawals (
    id UUID PRIMARY KEY,
    restaurant_id UUID REFERENCES restaurants(id),
    user_id UUID NOT NULL,
    amount BIGINT NOT NULL,
    reason TEXT NOT NULL,
    date BIGINT NOT NULL,
    sync_status TEXT NOT NULL DEFAULT 'SYNCED',
    updated_at BIGINT NOT NULL
);

CREATE INDEX idx_cash_withdrawals_restaurant ON cash_withdrawals(restaurant_id);
CREATE INDEX idx_cash_withdrawals_user ON cash_withdrawals(user_id);
CREATE INDEX idx_cash_withdrawals_date ON cash_withdrawals(date);
CREATE INDEX idx_cash_withdrawals_sync_status ON cash_withdrawals(sync_status);

-- Transaction items table
CREATE TABLE transaction_items (
    id UUID PRIMARY KEY,
    restaurant_id UUID REFERENCES restaurants(id),
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    product_id UUID NOT NULL,
    variant_id TEXT NOT NULL DEFAULT '',
    product_name TEXT NOT NULL, -- snapshot
    variant_name TEXT, -- snapshot
    qty INTEGER NOT NULL,
    unit_price BIGINT NOT NULL,
    subtotal BIGINT NOT NULL,
    sync_status TEXT NOT NULL DEFAULT 'SYNCED',
    updated_at BIGINT NOT NULL
);

CREATE INDEX idx_transaction_items_restaurant ON transaction_items(restaurant_id);
CREATE INDEX idx_transaction_items_transaction ON transaction_items(transaction_id);

-- General ledger table (cashflow tracking)
-- NOTE:
-- - Cash-affecting types (included in Saldo Kas): INITIAL_BALANCE, SALE, WITHDRAWAL, ADJUSTMENT
-- - Non-cash bookkeeping types (excluded from Saldo Kas): SALE_QRIS, COGS
CREATE TABLE general_ledger (
    id UUID PRIMARY KEY,
    restaurant_id UUID REFERENCES restaurants(id),
    type TEXT NOT NULL CHECK (type IN ('INITIAL_BALANCE', 'SALE', 'SALE_QRIS', 'WITHDRAWAL', 'ADJUSTMENT', 'COGS')),
    amount BIGINT NOT NULL,
    reference_id TEXT,
    description TEXT NOT NULL,
    date BIGINT NOT NULL,
    user_id TEXT NOT NULL,
    sync_status TEXT NOT NULL DEFAULT 'SYNCED',
    updated_at BIGINT NOT NULL
);

CREATE INDEX idx_general_ledger_restaurant ON general_ledger(restaurant_id);
CREATE INDEX idx_general_ledger_type ON general_ledger(type);
CREATE INDEX idx_general_ledger_date ON general_ledger(date);
CREATE INDEX idx_general_ledger_reference ON general_ledger(reference_id);
CREATE INDEX idx_general_ledger_sync_status ON general_ledger(sync_status);

-- Row Level Security (RLS) policies
-- For development: permissive policies. In production, implement proper auth.

ALTER TABLE restaurants ENABLE ROW LEVEL SECURITY;
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
ALTER TABLE general_ledger ENABLE ROW LEVEL SECURITY;

-- Create permissive policies (allow all operations)
-- WARNING: This is for development only. In production, implement proper auth.

CREATE POLICY "Allow all on restaurants" ON restaurants FOR ALL USING (true);
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
CREATE POLICY "Allow all on general_ledger" ON general_ledger FOR ALL USING (true);
