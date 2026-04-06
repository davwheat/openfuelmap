import { OpenAPIRoute } from "chanfana";
import { z } from "zod";
import type { AppContext } from "../types";

const DailyPriceStatSchema = z.object({
  date: z.string().openapi({ example: "2025-03-15" }),
  fuel_type: z.string().openapi({ example: "E10" }),
  price: z.number().openapi({ example: 132.9 }),
});

const ALLOWED_RANGES = [7, 28, 60, 90, 180, 365] as const;
type AllowedRange = (typeof ALLOWED_RANGES)[number];

const CACHE_TTL_SECONDS = 60 * 60;

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
    const days = parseInt(data.query.range, 10) as AllowedRange;

    const cacheKey = `daily-prices:v2:${stat}:${days}d`;

    const cached = await c.env.KV.get<z.infer<typeof DailyPriceStatSchema>[]>(
      cacheKey,
      "json",
    );
    if (cached) {
      return { success: true, result: { stat, prices: cached } };
    }

    const sql =
      stat === "trimmed_mean"
        ? `WITH daily_prices AS (
             SELECT
               fuel_type,
               DATE(price_change_effective_timestamp) AS date,
               price,
               PERCENT_RANK() OVER (
                 PARTITION BY fuel_type, DATE(price_change_effective_timestamp)
                 ORDER BY price
               ) AS pct
             FROM fuel_prices
             WHERE price_change_effective_timestamp >= DATE('now', '-' || ? || ' days')
           )
           SELECT
             date,
             fuel_type,
             ROUND(AVG(price), 1) AS price
           FROM daily_prices
           WHERE pct >= 0.1 AND pct <= 0.9
           GROUP BY fuel_type, date
           ORDER BY date, fuel_type`
        : `WITH daily_prices AS (
             SELECT
               fuel_type,
               DATE(price_change_effective_timestamp) AS date,
               price,
               ROW_NUMBER() OVER (
                 PARTITION BY fuel_type, DATE(price_change_effective_timestamp)
                 ORDER BY price
               ) AS rn,
               COUNT(*) OVER (
                 PARTITION BY fuel_type, DATE(price_change_effective_timestamp)
               ) AS cnt
             FROM fuel_prices
             WHERE price_change_effective_timestamp >= DATE('now', '-' || ? || ' days')
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
      .bind(days)
      .all<{ date: string; fuel_type: string; price: number }>();

    const prices = rows.results;

    await c.env.KV.put(cacheKey, JSON.stringify(prices), {
      expirationTtl: CACHE_TTL_SECONDS,
    });

    return { success: true, result: { stat, prices } };
  }
}
