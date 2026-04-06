import { OpenAPIRoute } from "chanfana";
import { z } from "zod";
import type { AppContext } from "../types";

const DailyMedianSchema = z.object({
  date: z.string().openapi({ example: "2025-03-15" }),
  fuel_type: z.string().openapi({ example: "E10" }),
  median_price: z.number().openapi({ example: 132.9 }),
});

const CACHE_KEY = "daily-median-prices:v1";
const CACHE_TTL_SECONDS = 60 * 60;

export class DailyMedianPrices extends OpenAPIRoute {
  schema = {
    tags: ["Stats"],
    summary: "Daily median fuel prices",
    description:
      "Returns the daily median price for each fuel type over the last 28 days",
    responses: {
      "200": {
        description: "Daily median prices per fuel type for the last 28 days",
        content: {
          "application/json": {
            schema: z.object({
              success: z.literal(true),
              result: z.object({
                prices: DailyMedianSchema.array(),
              }),
            }),
          },
        },
      },
    },
  };

  async handle(c: AppContext) {
    const cached = await c.env.KV.get<
      z.infer<typeof DailyMedianSchema>[]
    >(CACHE_KEY, "json");
    if (cached) {
      return { success: true, result: { prices: cached } };
    }

    const rows = await c.env.fuel_prices_db
      .prepare(
        `WITH daily_prices AS (
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
           WHERE price_change_effective_timestamp >= DATE('now', '-28 days')
         )
         SELECT
           date,
           fuel_type,
           ROUND(AVG(price), 1) AS median_price
         FROM daily_prices
         WHERE rn IN ((cnt + 1) / 2, (cnt + 2) / 2)
         GROUP BY fuel_type, date
         ORDER BY date, fuel_type`,
      )
      .all<{ date: string; fuel_type: string; median_price: number }>();

    const prices = rows.results;

    await c.env.KV.put(CACHE_KEY, JSON.stringify(prices), {
      expirationTtl: CACHE_TTL_SECONDS,
    });

    return { success: true, result: { prices } };
  }
}
