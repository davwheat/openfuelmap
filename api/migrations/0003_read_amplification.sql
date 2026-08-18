--  Open Fuel Map
--  Copyright (C) 2026  David Wheatley
--
--  This program is free software: you can redistribute it and/or modify
--  it under the terms of the GNU General Public License as published by
--  the Free Software Foundation, either version 3 of the License, or
--  (at your option) any later version.
--
--  This program is distributed in the hope that it will be useful,
--  but WITHOUT ANY WARRANTY; without even the implied warranty of
--  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
--  GNU General Public License for more details.
--
--  You should have received a copy of the GNU General Public License
--  along with this program.  If not, see <http://www.gnu.org/licenses/>.

-- Every "what does this station charge now" lookup also filters by fuel_type,
-- which idx_fuel_prices_is_latest (is_latest, node_id) cannot serve. The
-- planner fell back to idx_fuel_prices_history and walked a station's entire
-- price history for that fuel, discarding all but the newest row -- roughly
-- 20 rows scanned per row returned, growing with every day of history kept.
--
-- These partial indexes hold only the current prices, so the same lookups
-- become single-row seeks, and both are covering for the columns the API
-- reads back.
CREATE INDEX IF NOT EXISTS idx_fuel_prices_current
    ON fuel_prices (node_id, fuel_type, price, price_last_updated, price_change_effective_timestamp)
    WHERE is_latest = 1;

-- Serves the price-ordered scans: percentiles, the fuel type list, and
-- /api/prices sorted by price.
CREATE INDEX IF NOT EXISTS idx_fuel_prices_current_by_type
    ON fuel_prices (fuel_type, price, node_id)
    WHERE is_latest = 1;

-- idx_fuel_prices_current also covers (node_id) prefix lookups, and serves the
-- sync's is_latest reconciliation, so the old index has no remaining callers.
DROP INDEX IF EXISTS idx_fuel_prices_is_latest;

-- The sync's duplicate-detection query filters on the effective timestamp
-- alone. No existing index leads with that column, so it full-scanned
-- fuel_prices on every run.
CREATE INDEX IF NOT EXISTS idx_fuel_prices_effective_ts
    ON fuel_prices (price_change_effective_timestamp, node_id, fuel_type);

-- Daily aggregates were recomputed from the whole price history on every
-- rebuild: a date series cross-joined against every price row, so the cost was
-- days x rows and grew on both axes every day. The result for any past day
-- never changes, so store it once.
--
-- Keyed stat-first because reads always select a single stat over a date
-- range, which makes that an exact range scan.
CREATE TABLE IF NOT EXISTS daily_price_stats (
    date      TEXT NOT NULL,
    fuel_type TEXT NOT NULL,
    stat      TEXT NOT NULL,
    price     REAL NOT NULL,
    PRIMARY KEY (stat, date, fuel_type)
) WITHOUT ROWID;
