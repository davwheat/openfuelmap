import { OpenAPIRoute } from "chanfana";
import { z } from "zod";
import {
  type AppContext,
  ForecourtSummarySchema,
  getInaccuracyReason,
} from "../types";

export class ForecourtList extends OpenAPIRoute {
  schema = {
    tags: ["Forecourts"],
    summary: "List forecourt stations",
    request: {
      query: z.object({
        page: z.number().default(0).describe("Page number (0-indexed)"),
        limit: z.number().describe("Results per page"),
        brand: z
          .string()
          .optional()
          .describe("Filter by brand name (case-insensitive partial match)"),
        exclude_brand: z
          .string()
          .optional()
          .describe(
            "Exclude these brands from results. Comma-separated canonical brand names (e.g. 'BP,Shell,Independent'); exact, case-insensitive match",
          ),
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
      limit,
      brand,
      exclude_brand,
      postcode,
      fuel_type,
      sw_lat,
      sw_lng,
      ne_lat,
      ne_lng,
      include_closed,
    } = data.query;
    const offset = page * limit;

    const conditions: string[] = [];
    const whereParams: unknown[] = [];

    if (!include_closed) {
      conditions.push(
        "f.is_active = 1 AND f.temporary_closure = 0 AND (f.permanent_closure IS NULL OR f.permanent_closure = 0)",
      );
    } else {
      conditions.push("f.is_active = 1");
    }

    if (brand) {
      conditions.push("f.brand_name LIKE ?");
      whereParams.push(`%${brand}%`);
    }

    if (exclude_brand) {
      const excluded = exclude_brand
        .split(",")
        .map((s) => s.trim())
        .filter((s) => s.length > 0);
      if (excluded.length > 0) {
        const placeholders = excluded.map(() => "?").join(",");
        conditions.push(`LOWER(f.brand_name) NOT IN (${placeholders})`);
        whereParams.push(...excluded.map((s) => s.toLowerCase()));
      }
    }

    if (postcode) {
      conditions.push("f.postcode LIKE ?");
      whereParams.push(`${postcode}%`);
    }

    if (fuel_type) {
      conditions.push("f.fuel_types LIKE ?");
      whereParams.push(`%"${fuel_type}"%`);
    }

    if (
      sw_lat !== undefined &&
      sw_lng !== undefined &&
      ne_lat !== undefined &&
      ne_lng !== undefined
    ) {
      conditions.push(
        "f.latitude BETWEEN ? AND ? AND f.longitude BETWEEN ? AND ?",
      );
      whereParams.push(sw_lat, ne_lat, sw_lng, ne_lng);
    }

    const where =
      conditions.length > 0 ? `WHERE ${conditions.join(" AND ")}` : "";

    const countRow = await c.env.fuel_prices_db
      .prepare(`SELECT COUNT(*) as total FROM forecourts f ${where}`)
      .bind(...whereParams)
      .first<{ total: number }>();

    const priceJoin = fuel_type
      ? "LEFT JOIN fuel_prices fp ON fp.node_id = f.node_id AND fp.is_latest = 1 AND fp.fuel_type = ?"
      : "";
    const priceSelect = fuel_type
      ? `, fp.price as fp_price, fp.price_last_updated as fp_price_last_updated, fp.price_change_effective_timestamp as fp_price_change_effective_timestamp,
            (SELECT price FROM fuel_prices WHERE node_id = f.node_id AND fuel_type = ? AND is_latest = 0 ORDER BY price_change_effective_timestamp DESC LIMIT 1) as fp_prev_price`
      : "";
    const joinParams: unknown[] = fuel_type ? [fuel_type, fuel_type] : [];

    const rows = await c.env.fuel_prices_db
      .prepare(
        `SELECT f.node_id, f.trading_name, f.brand_name, f.postcode, f.city,
            f.latitude, f.longitude, f.is_motorway_service_station,
            f.is_supermarket_service_station, f.temporary_closure,
            f.permanent_closure, f.fuel_types${priceSelect}
         FROM forecourts f
         ${priceJoin}
         ${where}
         ORDER BY f.trading_name ASC
         LIMIT ? OFFSET ?`,
      )
      .bind(...joinParams, ...whereParams, limit, offset)
      .all();

    const forecourts = rows.results.map((row: Record<string, unknown>) => {
      const base = {
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
          row.permanent_closure === null
            ? null
            : Boolean(row.permanent_closure),
        fuel_types: JSON.parse(row.fuel_types as string),
      };
      if (!fuel_type) return base;

      let price = null;
      if (row.fp_price != null) {
        const current = row.fp_price as number;
        const prev = row.fp_prev_price as number | null;
        price = {
          price: current,
          price_last_updated: row.fp_price_last_updated as string,
          price_change_effective_timestamp:
            row.fp_price_change_effective_timestamp as string,
          previous_price: prev ?? null,
          price_change:
            prev != null ? (current > prev ? "increase" : "decrease") : null,
          possibly_inaccurate: getInaccuracyReason(
            current,
            row.fp_price_last_updated as string,
          ),
        };
      }

      return { ...base, price };
    });

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
