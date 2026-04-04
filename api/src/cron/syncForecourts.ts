import { SYNC_KEY_FORECOURTS } from "../config";
import { upsertForecourts } from "../db/forecourts";
import { getLastSync, setLastSync } from "../db/syncMeta";
import { fetchForecourts } from "../upstream/client";

export async function syncForecourts(
  db: D1Database,
  kv: KVNamespace,
  accessToken: string,
): Promise<{ fetched: number; upserted: number }> {
  const lastSync = await getLastSync(kv, SYNC_KEY_FORECOURTS);
  const since = lastSync || null;

  console.log(
    `[forecourts] Starting ${since ? `incremental sync since ${since}` : "full sync"}`,
  );

  const forecourts = await fetchForecourts(accessToken, since);
  console.log(
    `[forecourts] Fetched ${forecourts.length} forecourts from upstream`,
  );

  if (forecourts.length === 0) {
    console.log("[forecourts] Nothing to upsert, skipping DB write");
    return { fetched: 0, upserted: 0 };
  }

  const { upserted } = await upsertForecourts(db, forecourts);
  console.log(`[forecourts] Upserted ${upserted} forecourts into DB`);

  const today = new Date().toISOString().split("T")[0]!;
  await setLastSync(kv, SYNC_KEY_FORECOURTS, today);
  console.log(`[forecourts] Updated last sync timestamp to ${today}`);

  return { fetched: forecourts.length, upserted };
}
