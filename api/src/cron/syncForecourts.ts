import { SYNC_KEY_FORECOURTS, UK_BBOX } from "../config";
import { upsertForecourts } from "../db/forecourts";
import { getLastSync, setLastSync } from "../db/syncMeta";
import type { AccessTokenProvider } from "../upstream/auth";
import { fetchForecourts } from "../upstream/client";
import type { UpstreamForecourt } from "../upstream/types";

function isInUkBbox(lat: number, lon: number): boolean {
  return (
    lat >= UK_BBOX.minLat &&
    lat <= UK_BBOX.maxLat &&
    lon >= UK_BBOX.minLon &&
    lon <= UK_BBOX.maxLon
  );
}

function sanitizeForecourtCoordinates(forecourts: UpstreamForecourt[]): void {
  for (const fc of forecourts) {
    const { latitude, longitude } = fc.location;
    if (isInUkBbox(latitude, longitude)) continue;

    // Common data-entry bug: the negative sign on the longitude is dropped
    // so a UK site at -3.5 is recorded as 3.5. If flipping the sign lands
    // us back in the bbox, assume that's what happened and fix it.
    if (isInUkBbox(latitude, -longitude)) {
      console.warn(
        `[forecourts] Auto-fixed inverted longitude: node_id=${fc.node_id} trading_name=${JSON.stringify(fc.trading_name)} postcode=${JSON.stringify(fc.location.postcode)} lat=${latitude} lon=${longitude} -> ${-longitude}`,
      );
      fc.location.longitude = -longitude;
      continue;
    }

    console.warn(
      `[forecourts] Forecourt coordinates outside UK bbox: node_id=${fc.node_id} trading_name=${JSON.stringify(fc.trading_name)} brand=${JSON.stringify(fc.brand_name)} postcode=${JSON.stringify(fc.location.postcode)} lat=${latitude} lon=${longitude}`,
    );
  }
}

export async function syncForecourts(
  db: D1Database,
  kv: KVNamespace,
  tokenProvider: AccessTokenProvider,
): Promise<{ fetched: number; upserted: number }> {
  const lastSync = await getLastSync(kv, SYNC_KEY_FORECOURTS);
  const since = lastSync || null;

  console.log(
    `[forecourts] Starting ${since ? `incremental sync since ${since}` : "full sync"}`,
  );

  const forecourts = await fetchForecourts(tokenProvider, since);
  console.log(
    `[forecourts] Fetched ${forecourts.length} forecourts from upstream`,
  );

  sanitizeForecourtCoordinates(forecourts);

  let upserted = 0;

  if (forecourts.length === 0) {
    console.log("[forecourts] Nothing to upsert, skipping DB write");
  } else {
    ({ upserted } = await upsertForecourts(db, forecourts));
    console.log(`[forecourts] Upserted ${upserted} forecourts into DB`);
  }

  const today = new Date().toISOString().split("T")[0]!;
  await setLastSync(kv, SYNC_KEY_FORECOURTS, today);
  console.log(`[forecourts] Updated last sync timestamp to ${today}`);

  return { fetched: forecourts.length, upserted };
}
