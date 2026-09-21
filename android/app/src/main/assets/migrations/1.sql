-- 1. Shops Table
CREATE TABLE IF NOT EXISTS shops (
    id TEXT PRIMARY KEY NOT NULL,
    name TEXT NOT NULL,
    owner_name TEXT NOT NULL,
    phone TEXT NOT NULL UNIQUE,
    upi_id TEXT,
    currency_symbol TEXT NOT NULL DEFAULT '₹',
    pin_hash TEXT,
    settings_json TEXT NOT NULL DEFAULT '{}',
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

-- 2. Items Table
CREATE TABLE IF NOT EXISTS items (
    id TEXT PRIMARY KEY NOT NULL,
    shop_id TEXT NOT NULL,
    name TEXT NOT NULL,
    name_regional TEXT,
    category TEXT NOT NULL,
    barcode TEXT,
    unit_type TEXT NOT NULL CHECK(unit_type IN ('piece', 'weight')),
    price REAL NOT NULL,
    stock_qty REAL NOT NULL DEFAULT 0,
    low_stock_threshold REAL NOT NULL DEFAULT 5,
    is_active INTEGER NOT NULL DEFAULT 1,
    image_path TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_items_shop_active ON items(shop_id, is_active);
CREATE INDEX IF NOT EXISTS idx_items_barcode ON items(barcode);
CREATE INDEX IF NOT EXISTS idx_items_low_stock ON items(stock_qty, low_stock_threshold);

-- 3. Embeddings Table
CREATE TABLE IF NOT EXISTS embeddings (
    id TEXT PRIMARY KEY NOT NULL,
    item_id TEXT NOT NULL,
    vector BLOB NOT NULL,
    source TEXT NOT NULL CHECK(source IN ('setup', 'correction')),
    quality_score REAL NOT NULL DEFAULT 1.0,
    created_at INTEGER NOT NULL,
    FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_embeddings_item_id ON embeddings(item_id);

-- 4. Bills Table
CREATE TABLE IF NOT EXISTS bills (
    id TEXT PRIMARY KEY NOT NULL,
    shop_id TEXT NOT NULL,
    bill_number TEXT NOT NULL,
    subtotal REAL NOT NULL,
    discount REAL NOT NULL DEFAULT 0,
    tax REAL NOT NULL DEFAULT 0,
    total REAL NOT NULL,
    payment_method TEXT NOT NULL CHECK(payment_method IN ('cash', 'upi', 'credit')),
    created_at INTEGER NOT NULL,
    synced_at INTEGER
);

CREATE INDEX IF NOT EXISTS idx_bills_shop_date ON bills(shop_id, created_at);
CREATE INDEX IF NOT EXISTS idx_bills_synced ON bills(synced_at);

-- 5. Bill Items Table
CREATE TABLE IF NOT EXISTS bill_items (
    id TEXT PRIMARY KEY NOT NULL,
    bill_id TEXT NOT NULL,
    item_id TEXT NOT NULL,
    name_snapshot TEXT NOT NULL,
    qty REAL NOT NULL,
    unit_type TEXT NOT NULL,
    unit_price_snapshot REAL NOT NULL,
    line_total REAL NOT NULL,
    FOREIGN KEY (bill_id) REFERENCES bills(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_bill_items_bill_id ON bill_items(bill_id);

-- 6. Stock Log Table
CREATE TABLE IF NOT EXISTS stock_log (
    id TEXT PRIMARY KEY NOT NULL,
    item_id TEXT NOT NULL,
    change_qty REAL NOT NULL,
    reason TEXT NOT NULL CHECK(reason IN ('sale', 'restock', 'correction')),
    bill_id TEXT,
    created_at INTEGER NOT NULL,
    FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE,
    FOREIGN KEY (bill_id) REFERENCES bills(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_stock_log_item ON stock_log(item_id, created_at);

-- 7. Sync Queue Table
CREATE TABLE IF NOT EXISTS sync_queue (
    id TEXT PRIMARY KEY NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    operation TEXT NOT NULL CHECK(operation IN ('INSERT', 'UPDATE', 'DELETE')),
    payload_json TEXT NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sync_queue_retry ON sync_queue(retry_count, created_at);
