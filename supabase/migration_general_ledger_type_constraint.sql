-- Migration: expand general_ledger.type CHECK constraint
-- Adds non-cash ledger types used by app bookkeeping:
-- - SALE_QRIS (QRIS sales)
-- - COGS (goods receiving / cost of goods sold)
--
-- Run this once on existing Supabase databases.

ALTER TABLE general_ledger
DROP CONSTRAINT IF EXISTS general_ledger_type_check;

ALTER TABLE general_ledger
ADD CONSTRAINT general_ledger_type_check
CHECK (type IN ('INITIAL_BALANCE', 'SALE', 'SALE_QRIS', 'WITHDRAWAL', 'ADJUSTMENT', 'COGS'));
