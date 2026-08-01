/**
  Open Fuel Map
  Copyright (C) 2026  David Wheatley

  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

import { canonicalizeBrand } from "../brands";
import type { UpstreamForecourt } from "../upstream/types";

const UPSERT_SQL = `INSERT INTO forecourts (
	node_id, trading_name, brand_name, raw_brand_name, is_same_trading_and_brand_name,
	public_phone_number, temporary_closure, permanent_closure,
	permanent_closure_date, is_motorway_service_station,
	is_supermarket_service_station, address_line_1, address_line_2,
	city, country, county, postcode, latitude, longitude,
	amenities, opening_times, fuel_types, is_active, updated_at
) VALUES (
	?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, datetime('now')
) ON CONFLICT(node_id) DO UPDATE SET
	trading_name = excluded.trading_name,
	brand_name = excluded.brand_name,
	raw_brand_name = excluded.raw_brand_name,
	is_same_trading_and_brand_name = excluded.is_same_trading_and_brand_name,
	public_phone_number = excluded.public_phone_number,
	temporary_closure = excluded.temporary_closure,
	permanent_closure = excluded.permanent_closure,
	permanent_closure_date = excluded.permanent_closure_date,
	is_motorway_service_station = excluded.is_motorway_service_station,
	is_supermarket_service_station = excluded.is_supermarket_service_station,
	address_line_1 = excluded.address_line_1,
	address_line_2 = excluded.address_line_2,
	city = excluded.city,
	country = excluded.country,
	county = excluded.county,
	postcode = excluded.postcode,
	latitude = excluded.latitude,
	longitude = excluded.longitude,
	amenities = excluded.amenities,
	opening_times = excluded.opening_times,
	fuel_types = excluded.fuel_types,
	is_active = 1,
	updated_at = datetime('now')`;

export async function upsertForecourts(
  db: D1Database,
  forecourts: UpstreamForecourt[],
): Promise<{ upserted: number }> {
  let upserted = 0;

  const statements = forecourts.map((fc) =>
    db
      .prepare(UPSERT_SQL)
      .bind(
        fc.node_id,
        fc.trading_name,
        canonicalizeBrand(fc.brand_name, fc.location.address_line_1),
        fc.brand_name,
        fc.is_same_trading_and_brand_name ? 1 : 0,
        fc.public_phone_number,
        fc.temporary_closure ? 1 : 0,
        fc.permanent_closure === null ? null : fc.permanent_closure ? 1 : 0,
        fc.permanent_closure_date,
        fc.is_motorway_service_station ? 1 : 0,
        fc.is_supermarket_service_station ? 1 : 0,
        fc.location.address_line_1,
        fc.location.address_line_2,
        fc.location.city,
        fc.location.country,
        fc.location.county,
        fc.location.postcode,
        fc.location.latitude,
        fc.location.longitude,
        JSON.stringify(fc.amenities),
        JSON.stringify(fc.opening_times),
        JSON.stringify(fc.fuel_types),
      ),
  );

  const BATCH_SIZE = 100;
  for (let i = 0; i < statements.length; i += BATCH_SIZE) {
    const chunk = statements.slice(i, i + BATCH_SIZE);
    await db.batch(chunk);
    upserted += chunk.length;
  }

  return { upserted };
}
