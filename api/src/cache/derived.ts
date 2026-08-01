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

/**
 * Aggregates that are expensive to compute and only change when the sync
 * writes new data. The sync rebuilds them at the end of every run, so a
 * request should normally find them already cached; the endpoints keep a
 * compute-on-miss path purely as a fallback if the sync stops running.
 */

import { PRICE_PERCENTILE_HIGH, PRICE_PERCENTILE_LOW } from "../types";

export interface Brand {
  name: string;
  forecourt_count: number;
}

export interface PricePercentiles {
  low: number;
  high: number;
}

export interface DailyPriceStat {
  date: string;
  fuel_type: string;
  price: number;
}

export type DailyStat = "median" | "trimmed_mean";

export const BRANDS_CACHE_KEY = "brands:v1";
export const BRANDS_CACHE_TTL_SECONDS = 24 * 60 * 60;

export const PERCENTILES_CACHE_TTL_SECONDS = 6 * 60 * 60;
export const DAILY_STATS_CACHE_TTL_SECONDS = 6 * 60 * 60;

/** Earliest date to include in stats results (data before this is discarded). */
const STATS_MIN_DATE = "2026-03-25";
const STATS_MAX_RANGE_DAYS = 365;

/**
 * The daily stats query reads the whole price history, so it is not rebuilt
 * on every 20-minute sync — a slightly older aggregate is an acceptable
 * trade for not running it 72 times a day.
 */
const DAILY_STATS_MIN_REBUILD_INTERVAL_MS = 2 * 60 * 60 * 1000;
const DAILY_STATS_BUILT_AT_KEY = "daily-prices:v3:built-at";

export function percentilesCacheKey(fuelType: string): string {
  return `price-percentiles:${fuelType}`;
}

export function dailyStatsCacheKey(stat: DailyStat): string {
  return `daily-prices:v3:${stat}`;
}

export async function computeBrands(db: D1Database): Promise<Brand[]> {
  const rows = await db
    .prepare(
      `SELECT brand_name, COUNT(*) as forecourt_count
       FROM forecourts
       WHERE is_active = 1
       GROUP BY brand_name
       ORDER BY brand_name`,
    )
    .all<{ brand_name: string; forecourt_count: number }>();

  return rows.results.map((row) => ({
    name: row.brand_name,
    forecourt_count: row.forecourt_count,
  }));
}

export async function computePricePercentiles(
  db: D1Database,
  fuelType: string,
): Promise<PricePercentiles | null> {
  const row = await db
    .prepare(
      `WITH ranked AS (
         SELECT price, PERCENT_RANK() OVER (ORDER BY price) AS pct
         FROM fuel_prices fp
         JOIN forecourts f ON f.node_id = fp.node_id
         WHERE fp.is_latest = 1 AND fp.fuel_type = ? AND f.is_active = 1
       )
       SELECT
         ROUND(MIN(CASE WHEN pct >= ? THEN price END), 1) AS low,
         ROUND(MAX(CASE WHEN pct <= ? THEN price END), 1) AS high
       FROM ranked`,
    )
    .bind(fuelType, PRICE_PERCENTILE_LOW, PRICE_PERCENTILE_HIGH)
    .first<{ low: number | null; high: number | null }>();

  if (row?.low == null || row?.high == null) return null;
  return { low: row.low, high: row.high };
}

export async function computeDailyStats(
  db: D1Database,
  stat: DailyStat,
): Promise<DailyPriceStat[]> {
  // Shared CTEs: generate a date series and compute the active period for
  // every price row so we can find all prices in effect on each day.
  const sharedCtes = `
    RECURSIVE dates(date) AS (
      SELECT MAX(DATE('now', '-' || ? || ' days'), ?)
      UNION ALL
      SELECT DATE(date, '+1 day') FROM dates WHERE date < DATE('now')
    ),
    price_periods AS (
      SELECT
        fp.fuel_type,
        fp.price,
        DATE(fp.price_change_effective_timestamp) AS start_date,
        COALESCE(
          DATE(LEAD(fp.price_change_effective_timestamp) OVER (
            PARTITION BY fp.node_id, fp.fuel_type
            ORDER BY fp.price_change_effective_timestamp
          )),
          DATE('now', '+1 day')
        ) AS end_date
      FROM fuel_prices fp
      JOIN forecourts f ON f.node_id = fp.node_id AND f.is_active = 1
    )`;

  const sql =
    stat === "trimmed_mean"
      ? `WITH ${sharedCtes},
         daily_prices AS (
           SELECT
             d.date,
             pp.fuel_type,
             pp.price,
             PERCENT_RANK() OVER (
               PARTITION BY pp.fuel_type, d.date
               ORDER BY pp.price
             ) AS pct
           FROM dates d
           JOIN price_periods pp ON d.date >= pp.start_date AND d.date < pp.end_date
         )
         SELECT
           date,
           fuel_type,
           ROUND(AVG(price), 1) AS price
         FROM daily_prices
         WHERE pct >= 0.1 AND pct <= 0.9
         GROUP BY fuel_type, date
         ORDER BY date, fuel_type`
      : `WITH ${sharedCtes},
         daily_prices AS (
           SELECT
             d.date,
             pp.fuel_type,
             pp.price,
             ROW_NUMBER() OVER (
               PARTITION BY pp.fuel_type, d.date
               ORDER BY pp.price
             ) AS rn,
             COUNT(*) OVER (
               PARTITION BY pp.fuel_type, d.date
             ) AS cnt
           FROM dates d
           JOIN price_periods pp ON d.date >= pp.start_date AND d.date < pp.end_date
         )
         SELECT
           date,
           fuel_type,
           ROUND(AVG(price), 1) AS price
         FROM daily_prices
         WHERE rn IN ((cnt + 1) / 2, (cnt + 2) / 2)
         GROUP BY fuel_type, date
         ORDER BY date, fuel_type`;

  const rows = await db
    .prepare(sql)
    .bind(STATS_MAX_RANGE_DAYS, STATS_MIN_DATE)
    .all<DailyPriceStat>();

  return rows.results;
}

async function refreshBrands(db: D1Database, kv: KVNamespace): Promise<number> {
  const brands = await computeBrands(db);
  await kv.put(BRANDS_CACHE_KEY, JSON.stringify(brands), {
    expirationTtl: BRANDS_CACHE_TTL_SECONDS,
  });
  return brands.length;
}

async function refreshPercentiles(
  db: D1Database,
  kv: KVNamespace,
): Promise<number> {
  const fuelTypes = await db
    .prepare(
      "SELECT DISTINCT fuel_type FROM fuel_prices WHERE is_latest = 1 ORDER BY fuel_type",
    )
    .all<{ fuel_type: string }>();

  let refreshed = 0;
  for (const { fuel_type } of fuelTypes.results) {
    const percentiles = await computePricePercentiles(db, fuel_type);
    if (!percentiles) continue;
    await kv.put(percentilesCacheKey(fuel_type), JSON.stringify(percentiles), {
      expirationTtl: PERCENTILES_CACHE_TTL_SECONDS,
    });
    refreshed++;
  }
  return refreshed;
}

async function refreshDailyStats(
  db: D1Database,
  kv: KVNamespace,
  now: number,
): Promise<boolean> {
  const builtAt = await kv.get(DAILY_STATS_BUILT_AT_KEY);
  if (builtAt && now - Number(builtAt) < DAILY_STATS_MIN_REBUILD_INTERVAL_MS) {
    return false;
  }

  for (const stat of ["median", "trimmed_mean"] as const) {
    const rows = await computeDailyStats(db, stat);
    await kv.put(dailyStatsCacheKey(stat), JSON.stringify(rows), {
      expirationTtl: DAILY_STATS_CACHE_TTL_SECONDS,
    });
  }

  await kv.put(DAILY_STATS_BUILT_AT_KEY, String(now));
  return true;
}

export interface CacheRefreshResult {
  brands: number;
  percentiles: number;
  dailyStatsRebuilt: boolean;
  durationMs: number;
}

/**
 * Rebuilds every derived cache. Called at the end of a sync so the work
 * happens once per run instead of on whichever user request finds the cache
 * expired. Failures are logged and swallowed — a stale cache is not a reason
 * to fail the sync that already wrote its data successfully.
 */
export async function refreshDerivedCaches(
  db: D1Database,
  kv: KVNamespace,
): Promise<CacheRefreshResult> {
  const start = Date.now();
  const result: CacheRefreshResult = {
    brands: 0,
    percentiles: 0,
    dailyStatsRebuilt: false,
    durationMs: 0,
  };

  try {
    result.brands = await refreshBrands(db, kv);
  } catch (err) {
    console.error("[cache] Failed to refresh brands:", err);
  }

  try {
    result.percentiles = await refreshPercentiles(db, kv);
  } catch (err) {
    console.error("[cache] Failed to refresh price percentiles:", err);
  }

  try {
    result.dailyStatsRebuilt = await refreshDailyStats(db, kv, start);
  } catch (err) {
    console.error("[cache] Failed to refresh daily stats:", err);
  }

  result.durationMs = Date.now() - start;
  console.log(
    `[cache] Refreshed in ${result.durationMs}ms: brands=${result.brands}, percentiles=${result.percentiles}, dailyStatsRebuilt=${result.dailyStatsRebuilt}`,
  );
  return result;
}
