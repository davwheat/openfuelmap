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
import { FUEL_TYPE_NAMES } from "../config";

export interface Brand {
  name: string;
  forecourt_count: number;
}

export interface FuelType {
  id: string;
  name: string;
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

const DAILY_STATS: readonly DailyStat[] = ["median", "trimmed_mean"];

export const BRANDS_CACHE_KEY = "brands:v1";
export const BRANDS_CACHE_TTL_SECONDS = 24 * 60 * 60;

export const FUEL_TYPES_CACHE_KEY = "fuel-types:v1";
export const FUEL_TYPES_CACHE_TTL_SECONDS = 24 * 60 * 60;

export const PERCENTILES_CACHE_TTL_SECONDS = 6 * 60 * 60;
export const DAILY_STATS_CACHE_TTL_SECONDS = 6 * 60 * 60;

/** Earliest date to include in stats results (data before this is discarded). */
const STATS_MIN_DATE = "2026-03-25";

/** Longest lookback the endpoint offers, and so the most worth reading back. */
const STATS_MAX_RANGE_DAYS = 365;

/**
 * Completed days are rebuilt from price history in chunks so that a cold
 * daily_price_stats table (or a long outage) heals over several syncs instead
 * of putting one enormous query in front of a single run.
 */
const DAILY_STATS_MAX_BACKFILL_DAYS = 30;

const WRITE_BATCH_SIZE = 100;

export function percentilesCacheKey(fuelType: string): string {
  return `price-percentiles:${fuelType}`;
}

export function dailyStatsCacheKey(stat: DailyStat): string {
  return `daily-prices:v4:${stat}`;
}

function addDays(date: string, days: number): string {
  const ms = Date.parse(`${date}T00:00:00Z`) + days * 24 * 60 * 60 * 1000;
  return new Date(ms).toISOString().slice(0, 10);
}

function today(): string {
  return new Date().toISOString().slice(0, 10);
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

export async function computeFuelTypes(db: D1Database): Promise<FuelType[]> {
  const rows = await db
    .prepare(
      "SELECT DISTINCT fuel_type FROM fuel_prices WHERE is_latest = 1 ORDER BY fuel_type",
    )
    .all<{ fuel_type: string }>();

  return rows.results.map((row) => ({
    id: row.fuel_type,
    name: FUEL_TYPE_NAMES[row.fuel_type] ?? row.fuel_type,
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

/**
 * Rebuilds the aggregate for a range of completed days from price history.
 *
 * Reconstructs which price was in effect at each station on each day, so it
 * has to walk history -- the expensive path, and the reason results are stored
 * rather than recomputed. Callers keep the range short.
 *
 * Rows that take effect after the range cannot be in effect during it. They
 * are excluded from the scan, which is what makes backfilling an early chunk
 * cheaper than a late one; dropping them cannot shorten a period inside the
 * range, only one that already extends past its end.
 */
export async function computeDailyStatsForRange(
  db: D1Database,
  stat: DailyStat,
  from: string,
  to: string,
): Promise<DailyPriceStat[]> {
  const sharedCtes = `
    RECURSIVE dates(date) AS (
      SELECT ?
      UNION ALL
      SELECT DATE(date, '+1 day') FROM dates WHERE date < ?
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
      WHERE fp.price_change_effective_timestamp < DATE(?, '+1 day')
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
         WHERE pct >= ${PRICE_PERCENTILE_LOW} AND pct <= ${PRICE_PERCENTILE_HIGH}
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

  const rows = await db.prepare(sql).bind(from, to, to).all<DailyPriceStat>();
  return rows.results;
}

interface StoredStat {
  stat: DailyStat;
  date: string;
  fuel_type: string;
  price: number;
}

/**
 * Today's aggregate, taken from the current prices rather than reconstructed
 * from history.
 *
 * The two agree: a day's figure is built from the price in effect at the end
 * of it, and for today that is exactly the is_latest set. Reading it directly
 * costs one pass over the current prices instead of a pass over every price
 * ever recorded, which is why this runs on every sync while the history path
 * only fills in days it has never seen.
 *
 * The value is provisional until the day ends -- each sync overwrites it, and
 * the history path later rewrites the day once it is complete.
 */
async function computeCurrentDayStats(db: D1Database): Promise<StoredStat[]> {
  const date = today();
  const rows = await db
    .prepare(
      `WITH current_prices AS (
         SELECT
           fp.fuel_type,
           fp.price,
           ROW_NUMBER() OVER (PARTITION BY fp.fuel_type ORDER BY fp.price) AS rn,
           COUNT(*) OVER (PARTITION BY fp.fuel_type) AS cnt,
           PERCENT_RANK() OVER (PARTITION BY fp.fuel_type ORDER BY fp.price) AS pct
         FROM fuel_prices fp
         JOIN forecourts f ON f.node_id = fp.node_id AND f.is_active = 1
         WHERE fp.is_latest = 1
       )
       SELECT
         fuel_type,
         ROUND(AVG(CASE WHEN rn IN ((cnt + 1) / 2, (cnt + 2) / 2) THEN price END), 1) AS median,
         ROUND(AVG(CASE WHEN pct >= ? AND pct <= ? THEN price END), 1) AS trimmed_mean
       FROM current_prices
       GROUP BY fuel_type`,
    )
    .bind(PRICE_PERCENTILE_LOW, PRICE_PERCENTILE_HIGH)
    .all<{
      fuel_type: string;
      median: number | null;
      trimmed_mean: number | null;
    }>();

  const stats: StoredStat[] = [];
  for (const row of rows.results) {
    if (row.median != null) {
      stats.push({
        stat: "median",
        date,
        fuel_type: row.fuel_type,
        price: row.median,
      });
    }
    if (row.trimmed_mean != null) {
      stats.push({
        stat: "trimmed_mean",
        date,
        fuel_type: row.fuel_type,
        price: row.trimmed_mean,
      });
    }
  }
  return stats;
}

async function writeDailyStats(
  db: D1Database,
  stats: StoredStat[],
): Promise<void> {
  if (stats.length === 0) return;

  const statements = stats.map((s) =>
    db
      .prepare(
        `INSERT OR REPLACE INTO daily_price_stats (stat, date, fuel_type, price)
         VALUES (?, ?, ?, ?)`,
      )
      .bind(s.stat, s.date, s.fuel_type, s.price),
  );

  for (let i = 0; i < statements.length; i += WRITE_BATCH_SIZE) {
    await db.batch(statements.slice(i, i + WRITE_BATCH_SIZE));
  }
}

export async function readDailyStats(
  db: D1Database,
  stat: DailyStat,
): Promise<DailyPriceStat[]> {
  const rows = await db
    .prepare(
      `SELECT date, fuel_type, price
       FROM daily_price_stats
       WHERE stat = ? AND date >= MAX(?, DATE('now', '-' || ? || ' days'))
       ORDER BY date, fuel_type`,
    )
    .bind(stat, STATS_MIN_DATE, STATS_MAX_RANGE_DAYS)
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

async function refreshFuelTypes(
  db: D1Database,
  kv: KVNamespace,
): Promise<number> {
  const fuelTypes = await computeFuelTypes(db);
  await kv.put(FUEL_TYPES_CACHE_KEY, JSON.stringify(fuelTypes), {
    expirationTtl: FUEL_TYPES_CACHE_TTL_SECONDS,
  });
  return fuelTypes.length;
}

async function refreshPercentiles(
  db: D1Database,
  kv: KVNamespace,
): Promise<number> {
  const fuelTypes = await computeFuelTypes(db);

  let refreshed = 0;
  for (const { id } of fuelTypes) {
    const percentiles = await computePricePercentiles(db, id);
    if (!percentiles) continue;
    await kv.put(percentilesCacheKey(id), JSON.stringify(percentiles), {
      expirationTtl: PERCENTILES_CACHE_TTL_SECONDS,
    });
    refreshed++;
  }
  return refreshed;
}

/**
 * Stores the aggregate for any completed day not yet written, oldest first.
 *
 * Bounded per run: a cold table catches up over successive syncs rather than
 * running a single query across the entire history.
 */
async function finaliseCompletedDays(db: D1Database): Promise<number> {
  const now = today();
  const yesterday = addDays(now, -1);

  // Progress is read back from the rows themselves rather than tracked
  // alongside them, so the two cannot drift apart: a wiped table refills, and
  // history written by hand is picked up without being told about it.
  const latest = await db
    .prepare(
      "SELECT MAX(date) AS date FROM daily_price_stats WHERE stat = ? AND date < ?",
    )
    .bind(DAILY_STATS[0], now)
    .first<{ date: string | null }>();

  const from = latest?.date ? addDays(latest.date, 1) : STATS_MIN_DATE;
  if (from > yesterday) return 0;

  const chunkEnd = addDays(from, DAILY_STATS_MAX_BACKFILL_DAYS - 1);
  const to = chunkEnd < yesterday ? chunkEnd : yesterday;

  const stats: StoredStat[] = [];
  for (const stat of DAILY_STATS) {
    const computed = await computeDailyStatsForRange(db, stat, from, to);
    for (const row of computed) stats.push({ stat, ...row });
  }

  await writeDailyStats(db, stats);

  console.log(
    `[cache] Finalised daily stats for ${from}..${to} (${stats.length} rows)`,
  );
  return stats.length;
}

async function refreshDailyStats(
  db: D1Database,
  kv: KVNamespace,
): Promise<{ finalised: number; currentDay: number }> {
  const finalised = await finaliseCompletedDays(db);

  const currentDay = await computeCurrentDayStats(db);
  await writeDailyStats(db, currentDay);

  for (const stat of DAILY_STATS) {
    const rows = await readDailyStats(db, stat);
    await kv.put(dailyStatsCacheKey(stat), JSON.stringify(rows), {
      expirationTtl: DAILY_STATS_CACHE_TTL_SECONDS,
    });
  }

  return { finalised, currentDay: currentDay.length };
}

export interface CacheRefreshResult {
  brands: number;
  fuelTypes: number;
  percentiles: number;
  dailyStatsFinalised: number;
  dailyStatsCurrentDay: number;
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
    fuelTypes: 0,
    percentiles: 0,
    dailyStatsFinalised: 0,
    dailyStatsCurrentDay: 0,
    durationMs: 0,
  };

  try {
    result.brands = await refreshBrands(db, kv);
  } catch (err) {
    console.error("[cache] Failed to refresh brands:", err);
  }

  try {
    result.fuelTypes = await refreshFuelTypes(db, kv);
  } catch (err) {
    console.error("[cache] Failed to refresh fuel types:", err);
  }

  try {
    result.percentiles = await refreshPercentiles(db, kv);
  } catch (err) {
    console.error("[cache] Failed to refresh price percentiles:", err);
  }

  try {
    const daily = await refreshDailyStats(db, kv);
    result.dailyStatsFinalised = daily.finalised;
    result.dailyStatsCurrentDay = daily.currentDay;
  } catch (err) {
    console.error("[cache] Failed to refresh daily stats:", err);
  }

  result.durationMs = Date.now() - start;
  console.log(
    `[cache] Refreshed in ${result.durationMs}ms: brands=${result.brands}, fuelTypes=${result.fuelTypes}, percentiles=${result.percentiles}, dailyStatsFinalised=${result.dailyStatsFinalised}, dailyStatsCurrentDay=${result.dailyStatsCurrentDay}`,
  );
  return result;
}
