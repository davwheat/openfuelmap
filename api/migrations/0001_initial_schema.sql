-- Forecourt (PFS) stations
CREATE TABLE IF NOT EXISTS forecourts (
    node_id         TEXT PRIMARY KEY,
    trading_name    TEXT NOT NULL,
    brand_name      TEXT NOT NULL,
    is_same_trading_and_brand_name INTEGER NOT NULL DEFAULT 0,
    public_phone_number TEXT,
    temporary_closure   INTEGER NOT NULL DEFAULT 0,
    permanent_closure   INTEGER,
    permanent_closure_date TEXT,
    is_motorway_service_station    INTEGER NOT NULL DEFAULT 0,
    is_supermarket_service_station INTEGER NOT NULL DEFAULT 0,
    -- Location fields (flattened, always 1:1 with forecourt)
    address_line_1  TEXT NOT NULL,
    address_line_2  TEXT,
    city            TEXT NOT NULL,
    country         TEXT,
    county          TEXT,
    postcode        TEXT NOT NULL,
    latitude        REAL NOT NULL,
    longitude       REAL NOT NULL,
    -- Complex nested data stored as JSON
    amenities       TEXT NOT NULL DEFAULT '[]',
    opening_times   TEXT NOT NULL DEFAULT '{}',
    fuel_types      TEXT NOT NULL DEFAULT '[]',
    -- Soft deletion / metadata
    is_active       INTEGER NOT NULL DEFAULT 1,
    first_seen_at   TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at      TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_forecourts_lat_lng ON forecourts (latitude, longitude);
CREATE INDEX IF NOT EXISTS idx_forecourts_brand ON forecourts (brand_name);
CREATE INDEX IF NOT EXISTS idx_forecourts_postcode ON forecourts (postcode);
CREATE INDEX IF NOT EXISTS idx_forecourts_active ON forecourts (is_active);

-- Fuel prices (every observed price is a row, historic data preserved)
CREATE TABLE IF NOT EXISTS fuel_prices (
    id                               INTEGER PRIMARY KEY AUTOINCREMENT,
    node_id                          TEXT NOT NULL,
    fuel_type                        TEXT NOT NULL,
    price                            REAL NOT NULL,
    price_last_updated               TEXT NOT NULL,
    price_change_effective_timestamp TEXT NOT NULL,
    is_latest                        INTEGER NOT NULL DEFAULT 1,
    created_at                       TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (node_id) REFERENCES forecourts(node_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_fuel_prices_unique
    ON fuel_prices (node_id, fuel_type, price_change_effective_timestamp);
CREATE INDEX IF NOT EXISTS idx_fuel_prices_is_latest ON fuel_prices (is_latest, node_id);
CREATE INDEX IF NOT EXISTS idx_fuel_prices_history ON fuel_prices (node_id, fuel_type, price_change_effective_timestamp);
