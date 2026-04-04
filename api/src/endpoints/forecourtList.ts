import { OpenAPIRoute } from "chanfana";
import { z } from "zod";
import { DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE } from "../config";
import { type AppContext, ForecourtSummarySchema } from "../types";

export class ForecourtList extends OpenAPIRoute {
  schema = {
    tags: ["Forecourts"],
    summary: "List forecourt stations",
    request: {
      query: z.object({
        page: z.number().default(0).describe("Page number (0-indexed)"),
        limit: z
          .number()
          .default(DEFAULT_PAGE_SIZE)
          .describe(`Results per page (max ${MAX_PAGE_SIZE})`),
        brand: z
          .string()
          .optional()
          .describe("Filter by brand name (case-insensitive partial match)"),
        postcode: z.string().optional().describe("Filter by postcode prefix"),
        fuel_type: z
          .string()
          .optional()
          .describe(
            "Filter to stations offering this fuel type (e.g. E10, B7_STANDARD)",
          ),
        sw_lat: z
          .number()
          .optional()
          .describe("South-west corner latitude of bounding box"),
        sw_lng: z
          .number()
          .optional()
          .describe("South-west corner longitude of bounding box"),
        ne_lat: z
          .number()
          .optional()
          .describe("North-east corner latitude of bounding box"),
        ne_lng: z
          .number()
          .optional()
          .describe("North-east corner longitude of bounding box"),
        include_closed: z
          .boolean()
          .default(false)
          .describe("Include temporarily/permanently closed stations"),
      }),
    },
    responses: {
      "200": {
        description: "Returns a paginated list of forecourts",
        content: {
          "application/json": {
            schema: z.object({
              success: z.literal(true),
              result: z.object({
                forecourts: ForecourtSummarySchema.array(),
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
      brand,
      postcode,
      fuel_type,
      sw_lat,
      sw_lng,
      ne_lat,
      ne_lng,
      include_closed,
    } = data.query;
    const limit = Math.min(rawLimit, MAX_PAGE_SIZE);
    const offset = page * limit;

    const conditions: string[] = [];
    const params: unknown[] = [];

    if (!include_closed) {
      conditions.push(
        "is_active = 1 AND temporary_closure = 0 AND (permanent_closure IS NULL OR permanent_closure = 0)",
      );
    } else {
      conditions.push("is_active = 1");
    }

    if (brand) {
      conditions.push("brand_name LIKE ?");
      params.push(`%${brand}%`);
    }

    if (postcode) {
      conditions.push("postcode LIKE ?");
      params.push(`${postcode}%`);
    }

    if (fuel_type) {
      conditions.push("fuel_types LIKE ?");
      params.push(`%"${fuel_type}"%`);
    }

    if (
      sw_lat !== undefined &&
      sw_lng !== undefined &&
      ne_lat !== undefined &&
      ne_lng !== undefined
    ) {
      conditions.push("latitude BETWEEN ? AND ? AND longitude BETWEEN ? AND ?");
      params.push(sw_lat, ne_lat, sw_lng, ne_lng);
    }

    const where =
      conditions.length > 0 ? `WHERE ${conditions.join(" AND ")}` : "";

    const countRow = await c.env.fuel_prices_db
      .prepare(`SELECT COUNT(*) as total FROM forecourts ${where}`)
      .bind(...params)
      .first<{ total: number }>();

    const rows = await c.env.fuel_prices_db
      .prepare(
        `SELECT node_id, trading_name, brand_name, postcode, city,
					latitude, longitude, is_motorway_service_station,
					is_supermarket_service_station, temporary_closure,
					permanent_closure, fuel_types
			 FROM forecourts ${where}
			 ORDER BY trading_name ASC
			 LIMIT ? OFFSET ?`,
      )
      .bind(...params, limit, offset)
      .all();

    const forecourts = rows.results.map((row: Record<string, unknown>) => ({
      node_id: row.node_id,
      trading_name: row.trading_name,
      brand_name: row.brand_name,
      postcode: row.postcode,
      city: row.city,
      latitude: row.latitude,
      longitude: row.longitude,
      is_motorway_service_station: Boolean(row.is_motorway_service_station),
      is_supermarket_service_station: Boolean(
        row.is_supermarket_service_station,
      ),
      temporary_closure: Boolean(row.temporary_closure),
      permanent_closure:
        row.permanent_closure === null ? null : Boolean(row.permanent_closure),
      fuel_types: JSON.parse(row.fuel_types as string),
    }));

    return {
      success: true,
      result: {
        forecourts,
        total: countRow?.total ?? 0,
        page,
        limit,
      },
    };
  }
}
