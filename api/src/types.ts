import type { Context } from "hono";
import { z } from "zod";

export type AppContext = Context<{ Bindings: Env }>;

// --- Inaccuracy thresholds (adjust these to tune the possibly_inaccurate flag) ---

/** Prices below this value (in pence) are flagged as possibly inaccurate. */
export const INACCURACY_PRICE_THRESHOLD = 20;

/** Prices not updated within this many days are flagged as possibly inaccurate. */
export const STALE_PRICE_DAYS = 14;

export type InaccuracyReason = "price_too_low" | "stale_price" | null;

/**
 * Returns the reason a price may be inaccurate, or null if it looks fine.
 * Pass `priceLastUpdated` for current-price endpoints; omit it for
 * historical entries where staleness is expected.
 */
export function getInaccuracyReason(
  price: number,
  priceLastUpdated?: string,
): InaccuracyReason {
  if (price < INACCURACY_PRICE_THRESHOLD) return "price_too_low";
  if (priceLastUpdated) {
    const updatedMs = new Date(priceLastUpdated).getTime();
    const cutoffMs = Date.now() - STALE_PRICE_DAYS * 24 * 60 * 60 * 1000;
    if (updatedMs < cutoffMs) return "stale_price";
  }
  return null;
}

const InaccuracyReasonSchema = z
  .enum(["price_too_low", "stale_price"])
  .nullable()
  .openapi({
    description:
      "Reason the price may be inaccurate: price_too_low (below 20p), stale_price (not updated in 14+ days), or null if no issues detected",
    example: null,
  });

export const LocationSchema = z.object({
  address_line_1: z.string().openapi({ example: "14 LONDON ROAD" }),
  address_line_2: z.string().nullable(),
  city: z.string().openapi({ example: "LONDON" }),
  country: z.string().openapi({ example: "ENGLAND" }),
  county: z.string().nullable(),
  postcode: z.string().openapi({ example: "SW1A 1AA" }),
  latitude: z.number().openapi({ example: 51.5074 }),
  longitude: z.number().openapi({ example: -0.1278 }),
});

export const ForecourtSchema = z.object({
  node_id: z.string().openapi({
    example: "9b275ab576eeba3c6677984be15ee22a74e54fdfe8e5ea700e84a03178dc4ac1",
  }),
  trading_name: z.string().openapi({ example: "Shell Hammersmith" }),
  brand_name: z.string().openapi({ example: "Shell" }),
  is_same_trading_and_brand_name: z.boolean(),
  public_phone_number: z.string().nullable(),
  temporary_closure: z.boolean(),
  permanent_closure: z.boolean().nullable(),
  permanent_closure_date: z.string().nullable(),
  is_motorway_service_station: z.boolean(),
  is_supermarket_service_station: z.boolean(),
  location: LocationSchema,
  amenities: z.array(z.string()),
  opening_times: z.any(),
  fuel_types: z.array(z.string()),
  updated_at: z.string(),
});

export const ForecourtFuelPriceSchema = z.object({
  price: z.number().openapi({ example: 132.9 }),
  price_last_updated: z.string(),
  price_change_effective_timestamp: z.string(),
  previous_price: z.number().nullable().optional().openapi({ example: 134.9 }),
  price_change: z
    .enum(["increase", "decrease"])
    .nullable()
    .optional()
    .openapi({ example: "decrease" }),
  possibly_inaccurate: InaccuracyReasonSchema,
});

export const ForecourtSummarySchema = z.object({
  node_id: z.string(),
  trading_name: z.string(),
  brand_name: z.string(),
  postcode: z.string(),
  city: z.string(),
  latitude: z.number(),
  longitude: z.number(),
  is_motorway_service_station: z.boolean(),
  is_supermarket_service_station: z.boolean(),
  temporary_closure: z.boolean(),
  permanent_closure: z.boolean().nullable(),
  fuel_types: z.array(z.string()),
  price: ForecourtFuelPriceSchema.nullable().optional(),
});

export const FuelPriceSchema = z.object({
  fuel_type: z.string().openapi({ example: "E10" }),
  price: z.number().openapi({ example: 132.9 }),
  price_last_updated: z.string(),
  price_change_effective_timestamp: z.string(),
  possibly_inaccurate: InaccuracyReasonSchema,
});

export const StationPriceSchema = z.object({
  node_id: z.string(),
  trading_name: z.string(),
  brand_name: z.string(),
  postcode: z.string(),
  latitude: z.number(),
  longitude: z.number(),
  fuel_prices: z.array(FuelPriceSchema),
});

export const PriceHistoryEntrySchema = z.object({
  price: z.number().openapi({ example: 132.9 }),
  fuel_type: z.string().openapi({ example: "E10" }),
  price_change_effective_timestamp: z.string(),
  created_at: z.string(),
  possibly_inaccurate: InaccuracyReasonSchema,
});
