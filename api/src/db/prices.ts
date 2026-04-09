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

import type { UpstreamFuelPriceStation } from "../upstream/types";

const D1_MAX_BIND_PARAMS = 100;
const BATCH_SIZE = 100;

export async function upsertPrices(
  db: D1Database,
  stations: UpstreamFuelPriceStation[],
): Promise<{ inserted: number; skippedOrphans: number }> {
  let inserted = 0;
  let skippedOrphans = 0;

  // Pre-fetch existing forecourt node_ids for orphan detection
  // D1 limits bound params to 100 per statement
  const allNodeIds = [...new Set(stations.map((s) => s.node_id))];
  const existingNodeIds = new Set<string>();

  for (let i = 0; i < allNodeIds.length; i += D1_MAX_BIND_PARAMS) {
    const chunk = allNodeIds.slice(i, i + D1_MAX_BIND_PARAMS);
    const placeholders = chunk.map(() => "?").join(",");
    const rows = await db
      .prepare(
        `SELECT node_id FROM forecourts WHERE node_id IN (${placeholders})`,
      )
      .bind(...chunk)
      .all<{ node_id: string }>();
    for (const row of rows.results) {
      existingNodeIds.add(row.node_id);
    }
  }

  // Collect all statements across all stations.
  // Each fuel price produces 3 statements (clear is_latest, insert, repair)
  // kept together so they execute atomically within the same batch.
  const statements: D1PreparedStatement[] = [];

  for (const station of stations) {
    if (!existingNodeIds.has(station.node_id)) {
      skippedOrphans++;
      console.warn(
        `Skipping prices for unknown forecourt node_id=${station.node_id} (trading_name=${station.trading_name})`,
      );
      continue;
    }

    if (station.fuel_prices.length === 0) {
      continue;
    }

    for (const fp of station.fuel_prices) {
      statements.push(
        db
          .prepare(
            "UPDATE fuel_prices SET is_latest = 0 WHERE node_id = ? AND fuel_type = ? AND is_latest = 1",
          )
          .bind(station.node_id, fp.fuel_type),
      );

      statements.push(
        db
          .prepare(
            `INSERT OR IGNORE INTO fuel_prices (
              node_id, fuel_type, price, price_last_updated,
              price_change_effective_timestamp, is_latest, created_at
            ) VALUES (?, ?, ?, ?, ?, 1, datetime('now'))`,
          )
          .bind(
            station.node_id,
            fp.fuel_type,
            fp.price,
            fp.price_last_updated,
            fp.price_change_effective_timestamp,
          ),
      );

      statements.push(
        db
          .prepare(
            `UPDATE fuel_prices SET is_latest = 1
             WHERE id = (
               SELECT id FROM fuel_prices
               WHERE node_id = ? AND fuel_type = ?
               ORDER BY price_change_effective_timestamp DESC
               LIMIT 1
             ) AND is_latest = 0`,
          )
          .bind(station.node_id, fp.fuel_type),
      );
    }

    inserted += station.fuel_prices.length;
  }

  // Execute in batches — each batch is a D1 transaction
  for (let i = 0; i < statements.length; i += BATCH_SIZE) {
    const chunk = statements.slice(i, i + BATCH_SIZE);
    await db.batch(chunk);
    console.log(
      `[prices:db] Batch ${Math.floor(i / BATCH_SIZE) + 1}/${Math.ceil(statements.length / BATCH_SIZE)} — ${Math.min(i + BATCH_SIZE, statements.length)}/${statements.length} statements`,
    );
  }

  return { inserted, skippedOrphans };
}
