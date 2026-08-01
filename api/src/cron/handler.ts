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

import {
  refreshDerivedCaches,
  type CacheRefreshResult,
} from "../cache/derived";
import { createAccessTokenProvider } from "../upstream/auth";
import { syncForecourts } from "./syncForecourts";
import { syncPrices } from "./syncPrices";

export interface SyncResult {
  durationMs: number;
  forecourts: { fetched: number; upserted: number; durationMs: number };
  prices: {
    fetched: number;
    inserted: number;
    skippedDuplicates: number;
    skippedOrphans: number;
    durationMs: number;
  };
  caches: CacheRefreshResult;
}

export async function handleScheduled(env: Env): Promise<SyncResult> {
  const startTime = Date.now();
  console.log(`[cron] Sync started at ${new Date().toISOString()}`);

  try {
    const tokenProvider = createAccessTokenProvider(
      env.KV,
      env.UPSTREAM_CLIENT_ID,
      env.UPSTREAM_CLIENT_SECRET,
      {
        disableRefresh: env.DISABLE_OAUTH_REFRESH === true,
        disableCache: env.DISABLE_OAUTH_CACHE === true,
      },
    );
    // Prime so auth failures surface here, before we start any DB work.
    await tokenProvider.get();
    console.log("[cron] OAuth token acquired");

    // Sync forecourts first (reference data must exist before prices)
    const fcStart = Date.now();
    const forecourtResult = await syncForecourts(
      env.fuel_prices_db,
      env.KV,
      tokenProvider,
    );
    const fcDuration = Date.now() - fcStart;
    console.log(
      `[cron] Forecourt sync complete in ${fcDuration}ms: fetched=${forecourtResult.fetched}, upserted=${forecourtResult.upserted}`,
    );

    // Then sync prices
    const prStart = Date.now();
    const priceResult = await syncPrices(
      env.fuel_prices_db,
      env.KV,
      tokenProvider,
    );
    const prDuration = Date.now() - prStart;
    console.log(
      `[cron] Price sync complete in ${prDuration}ms: fetched=${priceResult.fetched}, inserted=${priceResult.inserted}, skippedDuplicates=${priceResult.skippedDuplicates}, skippedOrphans=${priceResult.skippedOrphans}`,
    );

    // Rebuild the derived caches now that the underlying data has changed,
    // so no user request has to compute them.
    const caches = await refreshDerivedCaches(env.fuel_prices_db, env.KV);

    const durationMs = Date.now() - startTime;
    console.log(`[cron] Sync finished successfully in ${durationMs}ms`);

    return {
      durationMs,
      forecourts: { ...forecourtResult, durationMs: fcDuration },
      prices: { ...priceResult, durationMs: prDuration },
      caches,
    };
  } catch (err) {
    console.error(`[cron] Sync failed after ${Date.now() - startTime}ms:`, err);
    throw err;
  }
}
