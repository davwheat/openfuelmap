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

import { OpenAPIRoute } from "chanfana";
import { z } from "zod";
import {
  DAILY_STATS_CACHE_TTL_SECONDS,
  dailyStatsCacheKey,
  readDailyStats,
  type DailyPriceStat,
} from "../cache/derived";
import type { AppContext } from "../types";

const DailyPriceStatSchema = z.object({
  date: z.string().openapi({ example: "2025-03-15" }),
  fuel_type: z.string().openapi({ example: "E10" }),
  price: z.number().openapi({ example: 132.9 }),
});

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

    const cacheKey = dailyStatsCacheKey(stat);

    let all = await c.env.KV.get<DailyPriceStat[]>(cacheKey, "json");

    if (!all) {
      // Fallback path: the sync normally keeps this warm. Reads the stored
      // aggregate rather than rebuilding it, so a cold cache cannot put a
      // history-wide scan behind a user request.
      all = await readDailyStats(c.env.fuel_prices_db, stat);

      c.executionCtx.waitUntil(
        c.env.KV.put(cacheKey, JSON.stringify(all), {
          expirationTtl: DAILY_STATS_CACHE_TTL_SECONDS,
        }),
      );
    }

    // Trim to the requested range
    const cutoff = new Date();
    cutoff.setUTCDate(cutoff.getUTCDate() - days);
    const cutoffStr = cutoff.toISOString().slice(0, 10);

    const prices = all.filter((p) => p.date >= cutoffStr);

    return { success: true, result: { stat, prices } };
  }
}
