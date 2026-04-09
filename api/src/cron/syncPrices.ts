import { SYNC_KEY_PRICES } from "../config";
import { upsertPrices } from "../db/prices";
import { getLastSync, setLastSync } from "../db/syncMeta";
import type { AccessTokenProvider } from "../upstream/auth";
import { fetchFuelPrices } from "../upstream/client";

export async function syncPrices(
  db: D1Database,
  kv: KVNamespace,
  tokenProvider: AccessTokenProvider,
): Promise<{ fetched: number; inserted: number; skippedOrphans: number }> {
  const lastSync = await getLastSync(kv, SYNC_KEY_PRICES);
  const since = lastSync || null;

  console.log(
    `[prices] Starting ${since ? `incremental sync since ${since}` : "full sync"}`,
  );

  const stations = await fetchFuelPrices(tokenProvider, since);
  const totalPrices = stations.reduce(
    (sum, s) => sum + s.fuel_prices.length,
    0,
  );
  console.log(
    `[prices] Fetched ${stations.length} stations with ${totalPrices} price entries from upstream`,
  );

  let inserted = 0;
  let skippedOrphans = 0;

  if (stations.length === 0) {
    console.log("[prices] Nothing to upsert, skipping DB write");
  } else {
    ({ inserted, skippedOrphans } = await upsertPrices(db, stations));
    console.log(
      `[prices] Upserted ${inserted} price entries into DB (${skippedOrphans} orphaned stations skipped)`,
    );
  }

  const today = new Date().toISOString().split("T")[0]!;
  await setLastSync(kv, SYNC_KEY_PRICES, today);
  console.log(`[prices] Updated last sync timestamp to ${today}`);

  return { fetched: totalPrices, inserted, skippedOrphans };
}
