import { getAccessToken } from "../upstream/auth";
import { syncForecourts } from "./syncForecourts";
import { syncPrices } from "./syncPrices";

export async function handleScheduled(env: Env): Promise<void> {
  const startTime = Date.now();
  console.log(`[cron] Sync started at ${new Date().toISOString()}`);

  try {
    const accessToken = await getAccessToken(
      env.KV,
      env.UPSTREAM_CLIENT_ID,
      env.UPSTREAM_CLIENT_SECRET,
    );
    console.log("[cron] OAuth token acquired");

    // Sync forecourts first (reference data must exist before prices)
    const fcStart = Date.now();
    const forecourtResult = await syncForecourts(
      env.fuel_prices_db,
      env.KV,
      accessToken,
    );
    console.log(
      `[cron] Forecourt sync complete in ${Date.now() - fcStart}ms: fetched=${forecourtResult.fetched}, upserted=${forecourtResult.upserted}`,
    );

    // Then sync prices
    const prStart = Date.now();
    const priceResult = await syncPrices(
      env.fuel_prices_db,
      env.KV,
      accessToken,
    );
    console.log(
      `[cron] Price sync complete in ${Date.now() - prStart}ms: fetched=${priceResult.fetched}, inserted=${priceResult.inserted}, skippedOrphans=${priceResult.skippedOrphans}`,
    );

    console.log(
      `[cron] Sync finished successfully in ${Date.now() - startTime}ms`,
    );
  } catch (err) {
    console.error(`[cron] Sync failed after ${Date.now() - startTime}ms:`, err);
    throw err;
  }
}
