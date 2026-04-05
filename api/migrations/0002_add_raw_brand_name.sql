-- Preserve the raw upstream brand_name so the canonicalisation done at
-- ingest time is non-lossy. brand_name will now hold the canonical value
-- (one of our hand-curated brands, or "Independent"); raw_brand_name holds
-- exactly what the upstream feed gave us.
ALTER TABLE forecourts ADD COLUMN raw_brand_name TEXT;

-- Backfill raw_brand_name with the existing (pre-canonicalisation) value.
UPDATE forecourts SET raw_brand_name = brand_name WHERE raw_brand_name IS NULL;
