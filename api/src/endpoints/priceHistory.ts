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
  type AppContext,
  getInaccuracyReason,
  PriceHistoryEntrySchema,
} from "../types";

export class PriceHistory extends OpenAPIRoute {
  schema = {
    tags: ["Prices"],
    summary: "Get price history for a forecourt",
    request: {
      params: z.object({
        nodeId: z.string().describe("Forecourt node_id"),
      }),
      query: z.object({
        fuel_type: z
          .string()
          .optional()
          .describe(
            "Filter to specific fuel type (e.g. E10). Omit for all types.",
          ),
        since: z
          .string()
          .optional()
          .describe("Only return prices after this date (YYYY-MM-DD)"),
        limit: z
          .number()
          .default(500)
          .describe("Max number of price entries to return"),
      }),
    },
    responses: {
      "200": {
        description: "Returns historical price entries ordered by timestamp",
        content: {
          "application/json": {
            schema: z.object({
              success: z.literal(true),
              result: z.object({
                node_id: z.string(),
                prices: PriceHistoryEntrySchema.array(),
              }),
            }),
          },
        },
      },
      "404": {
        description: "Forecourt not found",
        content: {
          "application/json": {
            schema: z.object({
              success: z.literal(false),
              error: z.string(),
            }),
          },
        },
      },
    },
  };

  async handle(c: AppContext) {
    const data = await this.getValidatedData<typeof this.schema>();
    const { nodeId } = data.params;
    const { fuel_type, since, limit } = data.query;

    const exists = await c.env.fuel_prices_db
      .prepare("SELECT 1 FROM forecourts WHERE node_id = ?")
      .bind(nodeId)
      .first();

    if (!exists) {
      return Response.json(
        { success: false, error: "Forecourt not found" },
        { status: 404 },
      );
    }

    const conditions: string[] = ["node_id = ?"];
    const params: unknown[] = [nodeId];

    if (fuel_type) {
      conditions.push("fuel_type = ?");
      params.push(fuel_type);
    }
    if (since) {
      conditions.push("price_change_effective_timestamp >= ?");
      params.push(since);
    }

    const where = conditions.join(" AND ");

    const rows = await c.env.fuel_prices_db
      .prepare(
        `SELECT price, fuel_type, price_change_effective_timestamp, created_at
			 FROM fuel_prices
			 WHERE ${where}
			 ORDER BY price_change_effective_timestamp ASC
			 LIMIT ?`,
      )
      .bind(...params, limit)
      .all();

    return {
      success: true,
      result: {
        node_id: nodeId,
        prices: rows.results.map((p: Record<string, unknown>) => ({
          ...p,
          possibly_inaccurate: getInaccuracyReason(p.price as number),
        })),
      },
    };
  }
}
