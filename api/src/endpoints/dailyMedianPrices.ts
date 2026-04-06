import { OpenAPIRoute } from "chanfana";
import { z } from "zod";
import type { AppContext } from "../types";

const DailyPriceStatSchema = z.object({
  date: z.string().openapi({ example: "2025-03-15" }),
  fuel_type: z.string().openapi({ example: "E10" }),
  price: z.number().openapi({ example: 132.9 }),
});

/** Earliest date to include in stats results (data before this is discarded). */
const STATS_MIN_DATE = "2026-03-25";

const MAX_RANGE_DAYS = 365;
const CACHE_TTL_SECONDS = 2 * 60 * 60;

export class DailyMedianPrices extends OpenAPIRoute {
  schema = {
    tags: ["Stats"],
    summary: "Daily fuel price statistics",
    description:
      "Returns a daily price statistic (median or 10th–90th percentile trimmed mean) for each fuel type over a given range",
    request: {
      query: z.object({
        range: z
          .enum(["7d", "28d", "60d", "90d", "180d", "365d"])
          .default("28d")
          .describe("Lookback period"),
        stat: z
          .enum(["median", "trimmed_mean"])
          .default("median")
          .describe(
            "Statistic to compute: median or 10th–90th percentile trimmed mean",
          ),
      }),
    },
    responses: {
      "200": {
        description: "Daily price statistics per fuel type",
        content: {
          "application/json": {
            schema: z.object({
              success: z.literal(true),
              result: z.object({
                stat: z.enum(["median", "trimmed_mean"]),
                prices: DailyPriceStatSchema.array(),
              }),
            }),
          },
        },
      },
    },
  };

  async handle(c: AppContext) {
    const data = await this.getValidatedData<typeof this.schema>();
    const { stat } = data.query;
    const days = parseInt(data.query.range, 10);

    const cacheKey = `daily-prices:v3:${stat}`;

    let all = await c.env.KV.get<z.infer<typeof DailyPriceStatSchema>[]>(
      cacheKey,
      "json",
    );

    if (!all) {
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

      const rows = await c.env.fuel_prices_db
        .prepare(sql)
        .bind(MAX_RANGE_DAYS, STATS_MIN_DATE)
        .all<{ date: string; fuel_type: string; price: number }>();

      all = rows.results;

      await c.env.KV.put(cacheKey, JSON.stringify(all), {
        expirationTtl: CACHE_TTL_SECONDS,
      });
    }

    // Trim to the requested range
    const cutoff = new Date();
    cutoff.setUTCDate(cutoff.getUTCDate() - days);
    const cutoffStr = cutoff.toISOString().slice(0, 10);

    const prices = all.filter((p) => p.date >= cutoffStr);

    return { success: true, result: { stat, prices } };
  }
}
