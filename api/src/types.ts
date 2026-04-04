import type { Context } from "hono";
import { z } from "zod";

export type AppContext = Context<{ Bindings: Env }>;

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
});

export const FuelPriceSchema = z.object({
  fuel_type: z.string().openapi({ example: "E10" }),
  price: z.number().openapi({ example: 132.9 }),
  price_last_updated: z.string(),
  price_change_effective_timestamp: z.string(),
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
});
