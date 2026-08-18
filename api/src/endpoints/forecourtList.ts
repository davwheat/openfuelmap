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
  computePricePercentiles,
  PERCENTILES_CACHE_TTL_SECONDS,
  percentilesCacheKey,
  type PricePercentiles,
} from "../cache/derived";
import {
  type AppContext,
  ForecourtSummarySchema,
  PRICE_CHANGE_MAX_AGE_HOURS,
  getInaccuracyReason,
} from "../types";

const PricePercentilesSchema = z.object({
  low: z.number().openapi({ example: 127.9 }),
  high: z.number().openapi({ example: 145.9 }),
});

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
                price_percentiles: PricePercentilesSchema.nullable().openapi({
                  description:
                    "10th/90th percentile prices across all active stations for the requested fuel type (null when no fuel_type filter)",
                }),
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

    // A page that came back short is the last one, so its length already gives
    // the total. Only a full page leaves the question open, and only then is
    // the extra count worth scanning for.
    const total =
      rows.results.length < limit
        ? offset + rows.results.length
        : ((
            await c.env.fuel_prices_db
              .prepare(`SELECT COUNT(*) as total FROM forecourts f ${where}`)
              .bind(...whereParams)
              .first<{ total: number }>()
          )?.total ?? 0);

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
        const changeEffectiveMs = new Date(
          row.fp_price_change_effective_timestamp as string,
        ).getTime();
        const changeCutoffMs =
          Date.now() - PRICE_CHANGE_MAX_AGE_HOURS * 60 * 60 * 1000;
        const isRecentChange = changeEffectiveMs >= changeCutoffMs;

        price = {
          price: current,
          price_last_updated: row.fp_price_last_updated as string,
          price_change_effective_timestamp:
            row.fp_price_change_effective_timestamp as string,
          previous_price: prev ?? null,
          price_change:
            prev != null && isRecentChange
              ? current > prev
                ? "increase"
                : "decrease"
              : null,
          possibly_inaccurate: getInaccuracyReason(
            current,
            row.fp_price_last_updated as string,
          ),
        };
      }

      return { ...base, price };
    });

    let price_percentiles: PricePercentiles | null = null;
    if (fuel_type) {
      const cacheKey = percentilesCacheKey(fuel_type);
      price_percentiles = await c.env.KV.get<PricePercentiles>(
        cacheKey,
        "json",
      );

      if (!price_percentiles) {
        // Fallback path: the sync normally keeps this warm.
        price_percentiles = await computePricePercentiles(
          c.env.fuel_prices_db,
          fuel_type,
        );

        if (price_percentiles) {
          c.executionCtx.waitUntil(
            c.env.KV.put(cacheKey, JSON.stringify(price_percentiles), {
              expirationTtl: PERCENTILES_CACHE_TTL_SECONDS,
            }),
          );
        }
      }
    }

    return {
      success: true,
      result: {
        forecourts,
        price_percentiles,
        total,
        page,
        limit,
      },
    };
  }
}
