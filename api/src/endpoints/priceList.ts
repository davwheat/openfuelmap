import { OpenAPIRoute } from "chanfana";
import { z } from "zod";
import { DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE } from "../config";
import { type AppContext, StationPriceSchema } from "../types";

export class PriceList extends OpenAPIRoute {
  schema = {
    tags: ["Prices"],
    summary: "List current fuel prices",
    request: {
      query: z.object({
        page: z.number().default(0).describe("Page number (0-indexed)"),
        limit: z
          .number()
          .default(DEFAULT_PAGE_SIZE)
          .describe("Results per page"),
        fuel_type: z
          .string()
          .optional()
          .describe("Filter by fuel type (e.g. E10, E5, B7_STANDARD)"),
        brand: z.string().optional().describe("Filter by brand name"),
        postcode: z.string().optional().describe("Filter by postcode prefix"),
        sort: z
          .enum(["price_asc", "price_desc"])
          .default("price_asc")
          .describe("Sort order"),
      }),
    },
    responses: {
      "200": {
        description: "Returns latest prices grouped by station",
        content: {
          "application/json": {
            schema: z.object({
              success: z.literal(true),
              result: z.object({
                stations: StationPriceSchema.array(),
                total: z.number(),
                page: z.number(),
                limit: z.number(),
              }),
            }),
          },
        },
      },
    },
  };

  async handle(c: AppContext) {
    const data = await this.getValidatedData<typeof this.schema>();
    const {
      page,
      limit: rawLimit,
      fuel_type,
      brand,
      postcode,
      sort,
    } = data.query;
    const limit = Math.min(rawLimit, MAX_PAGE_SIZE);
    const offset = page * limit;

    const conditions: string[] = ["fp.is_latest = 1", "f.is_active = 1"];
    const params: unknown[] = [];

    if (fuel_type) {
      conditions.push("fp.fuel_type = ?");
      params.push(fuel_type);
    }
    if (brand) {
      conditions.push("f.brand_name LIKE ?");
      params.push(`%${brand}%`);
    }
    if (postcode) {
      conditions.push("f.postcode LIKE ?");
      params.push(`${postcode}%`);
    }

    const where = conditions.join(" AND ");
    const sortDir = sort === "price_desc" ? "DESC" : "ASC";

    const countRow = await c.env.fuel_prices_db
      .prepare(
        `SELECT COUNT(DISTINCT f.node_id) as total
			 FROM forecourts f
			 JOIN fuel_prices fp ON f.node_id = fp.node_id
			 WHERE ${where}`,
      )
      .bind(...params)
      .first<{ total: number }>();

    const stationRows = await c.env.fuel_prices_db
      .prepare(
        `SELECT DISTINCT f.node_id, f.trading_name, f.brand_name,
					f.postcode, f.latitude, f.longitude,
					MIN(fp.price) as min_price
			 FROM forecourts f
			 JOIN fuel_prices fp ON f.node_id = fp.node_id
			 WHERE ${where}
			 GROUP BY f.node_id
			 ORDER BY min_price ${sortDir}
			 LIMIT ? OFFSET ?`,
      )
      .bind(...params, limit, offset)
      .all();

    const stationIds = stationRows.results.map(
      (r: Record<string, unknown>) => r.node_id as string,
    );

    // Fetch all prices for the page in a single query
    let pricesByStation: Record<string, Record<string, unknown>[]> = {};
    if (stationIds.length > 0) {
      const placeholders = stationIds.map(() => "?").join(",");
      const priceRows = await c.env.fuel_prices_db
        .prepare(
          `SELECT node_id, fuel_type, price, price_last_updated, price_change_effective_timestamp
				 FROM fuel_prices
				 WHERE node_id IN (${placeholders}) AND is_latest = 1
				 ORDER BY fuel_type`,
        )
        .bind(...stationIds)
        .all();

      for (const row of priceRows.results as Record<string, unknown>[]) {
        const nodeId = row.node_id as string;
        (pricesByStation[nodeId] ??= []).push({
          fuel_type: row.fuel_type,
          price: row.price,
          price_last_updated: row.price_last_updated,
          price_change_effective_timestamp:
            row.price_change_effective_timestamp,
        });
      }
    }

    const stations = stationRows.results.map(
      (sRow: Record<string, unknown>) => ({
        node_id: sRow.node_id,
        trading_name: sRow.trading_name,
        brand_name: sRow.brand_name,
        postcode: sRow.postcode,
        latitude: sRow.latitude,
        longitude: sRow.longitude,
        fuel_prices: pricesByStation[sRow.node_id as string] ?? [],
      }),
    );

    return {
      success: true,
      result: {
        stations,
        total: countRow?.total ?? 0,
        page,
        limit,
      },
    };
  }
}
