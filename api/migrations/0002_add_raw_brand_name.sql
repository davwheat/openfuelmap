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

-- Preserve the raw upstream brand_name so the canonicalisation done at
-- ingest time is non-lossy. brand_name will now hold the canonical value
-- (one of our hand-curated brands, or "Independent"); raw_brand_name holds
-- exactly what the upstream feed gave us.
ALTER TABLE forecourts ADD COLUMN raw_brand_name TEXT;

-- Backfill raw_brand_name with the existing (pre-canonicalisation) value.
UPDATE forecourts SET raw_brand_name = brand_name WHERE raw_brand_name IS NULL;
