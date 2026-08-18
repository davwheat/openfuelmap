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

-- Indexes that cost writes and storage without ever being read.

-- Exactly the columns of idx_fuel_prices_unique, in the same order, so it has
-- never been able to answer a query that index could not. Every caller --
-- price history, the sync's is_latest reconciliation, the previous-price
-- lookup -- plans identically without it. Around a fifth of the database.
DROP INDEX IF EXISTS idx_fuel_prices_history;

-- is_active is a soft-delete flag that is set on virtually every row, so
-- indexing it alone cannot narrow anything down. No query picks it: the
-- bounding box, brand grouping and node lookups all have a better option.
DROP INDEX IF EXISTS idx_forecourts_active;

-- Rebuilt with the collation the postcode filter needs. SQLite's LIKE is
-- case-insensitive by default, and it will not answer `postcode LIKE 'SW1%'`
-- from a BINARY-collated index -- so the filter scanned every forecourt while
-- an index that could have served it sat unused. NOCASE lets the prefix match
-- become a range seek.
DROP INDEX IF EXISTS idx_forecourts_postcode;
CREATE INDEX IF NOT EXISTS idx_forecourts_postcode
    ON forecourts (postcode COLLATE NOCASE);
