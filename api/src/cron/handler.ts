import { createAccessTokenProvider } from "../upstream/auth";
import { syncForecourts } from "./syncForecourts";
import { syncPrices } from "./syncPrices";

export interface SyncResult {
  durationMs: number;
  forecourts: { fetched: number; upserted: number; durationMs: number };
  prices: {
    fetched: number;
    inserted: number;
    skippedOrphans: number;
    durationMs: number;
  };
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
      `[cron] Price sync complete in ${prDuration}ms: fetched=${priceResult.fetched}, inserted=${priceResult.inserted}, skippedOrphans=${priceResult.skippedOrphans}`,
    );

    const durationMs = Date.now() - startTime;
    console.log(`[cron] Sync finished successfully in ${durationMs}ms`);

    return {
      durationMs,
      forecourts: { ...forecourtResult, durationMs: fcDuration },
      prices: { ...priceResult, durationMs: prDuration },
    };
  } catch (err) {
    console.error(`[cron] Sync failed after ${Date.now() - startTime}ms:`, err);
    throw err;
  }
}
