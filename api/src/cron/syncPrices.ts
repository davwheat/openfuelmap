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

import { SYNC_KEY_PRICES } from "../config";
import {
  loadKnownForecourtIds,
  loadRecentPriceKeys,
  upsertPriceBatch,
  type PriceSyncState,
} from "../db/prices";
import { getLastSync, setLastSync } from "../db/syncMeta";
import type { AccessTokenProvider } from "../upstream/auth";
import { fetchFuelPrices } from "../upstream/client";

export async function syncPrices(
  db: D1Database,
  kv: KVNamespace,
  tokenProvider: AccessTokenProvider,
): Promise<{
  fetched: number;
  inserted: number;
  skippedDuplicates: number;
  skippedOrphans: number;
}> {
  const lastSync = await getLastSync(kv, SYNC_KEY_PRICES);
  const since = lastSync || null;

  // Captured before fetching so changes that become effective during this
  // run are picked up next time rather than falling into the gap.
  const syncStartedDate = new Date().toISOString().split("T")[0]!;

  console.log(
    `[prices] Starting ${since ? `incremental sync since ${since}` : "full sync"}`,
  );

  const [knownNodeIds, seenPriceKeys] = await Promise.all([
    loadKnownForecourtIds(db),
    loadRecentPriceKeys(db, since),
  ]);
  console.log(
    `[prices] ${knownNodeIds.size} known forecourts, ${seenPriceKeys.size} price entries already stored since ${since ?? "the beginning"}`,
  );

  const state: PriceSyncState = { knownNodeIds, seenPriceKeys };
  let fetched = 0;
  let inserted = 0;
  let skippedDuplicates = 0;
  let skippedOrphans = 0;

  const stationCount = await fetchFuelPrices(
    tokenProvider,
    since,
    async (stations, batchNumber) => {
      for (const s of stations) fetched += s.fuel_prices.length;

      const result = await upsertPriceBatch(db, stations, state);
      inserted += result.inserted;
      skippedDuplicates += result.skippedDuplicates;
      skippedOrphans += result.skippedOrphans;
      console.log(
        `[prices] Batch ${batchNumber}: ${stations.length} stations — inserted=${result.inserted}, duplicates=${result.skippedDuplicates}, orphans=${result.skippedOrphans}`,
      );
    },
  );

  console.log(
    `[prices] Fetched ${stationCount} stations with ${fetched} price entries; inserted ${inserted} new (${skippedDuplicates} already stored, ${skippedOrphans} orphaned stations skipped)`,
  );

  await setLastSync(kv, SYNC_KEY_PRICES, syncStartedDate);
  console.log(`[prices] Updated last sync timestamp to ${syncStartedDate}`);

  return { fetched, inserted, skippedDuplicates, skippedOrphans };
}
