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

import { z } from "zod";

export const UpstreamDayOpeningSchema = z.object({
  open: z.string(),
  close: z.string(),
  is_24_hours: z.boolean(),
});

export const UpstreamOpeningTimesSchema = z.object({
  usual_days: z.object({
    monday: UpstreamDayOpeningSchema,
    tuesday: UpstreamDayOpeningSchema,
    wednesday: UpstreamDayOpeningSchema,
    thursday: UpstreamDayOpeningSchema,
    friday: UpstreamDayOpeningSchema,
    saturday: UpstreamDayOpeningSchema,
    sunday: UpstreamDayOpeningSchema,
  }),
  bank_holiday: z.object({
    type: z.string(),
    open_time: z.string(),
    close_time: z.string(),
    is_24_hours: z.boolean(),
  }),
});

export const UpstreamLocationSchema = z.object({
  address_line_1: z.string(),
  address_line_2: z.string().nullable(),
  city: z.string(),
  country: z.string().nullable(),
  county: z.string().nullable(),
  postcode: z.string(),
  latitude: z.number(),
  longitude: z.number(),
});

export const UpstreamForecourtSchema = z.object({
  node_id: z.string(),
  trading_name: z.string(),
  brand_name: z.string(),
  is_same_trading_and_brand_name: z.boolean(),
  public_phone_number: z.string().nullable(),
  temporary_closure: z.boolean(),
  permanent_closure: z.boolean().nullable(),
  permanent_closure_date: z.string().nullable(),
  is_motorway_service_station: z.boolean(),
  is_supermarket_service_station: z.boolean(),
  location: UpstreamLocationSchema,
  amenities: z.array(z.string()),
  opening_times: UpstreamOpeningTimesSchema,
  fuel_types: z.array(z.string()),
});

export const UpstreamForecourtResponseSchema = z.array(UpstreamForecourtSchema);

export const UpstreamFuelPriceEntrySchema = z.object({
  fuel_type: z.string(),
  price: z.number(),
  price_last_updated: z.string(),
  price_change_effective_timestamp: z.string(),
});

export const UpstreamFuelPriceStationSchema = z.object({
  node_id: z.string(),
  trading_name: z.string(),
  public_phone_number: z.union([z.string(), z.number()]).nullable(),
  fuel_prices: z.array(UpstreamFuelPriceEntrySchema),
});

export const UpstreamFuelPriceResponseSchema = z.array(
  UpstreamFuelPriceStationSchema,
);

export type UpstreamForecourt = z.infer<typeof UpstreamForecourtSchema>;
export type UpstreamFuelPriceStation = z.infer<
  typeof UpstreamFuelPriceStationSchema
>;
export type UpstreamFuelPriceEntry = z.infer<
  typeof UpstreamFuelPriceEntrySchema
>;

export const UpstreamTokenResponseSchema = z.object({
  success: z.boolean(),
  data: z.object({
    access_token: z.string(),
    token_type: z.string().optional(),
    expires_in: z.number().optional(),
    refresh_token: z.string().optional(),
  }),
  message: z.string().optional(),
});

/** Flat response shape matching the OpenAPI spec for `regenerate_access_token`. */
const UpstreamRegenerateTokenFlatSchema = z.object({
  access_token: z.string(),
  token_type: z.string().optional(),
  expires_in: z.number().optional(),
});

/**
 * Wrapped response shape for `regenerate_access_token`. In practice the
 * upstream API returns the same `{ success, data, message }` wrapper as
 * `generate_access_token`, despite the OpenAPI spec suggesting a flat payload.
 */
const UpstreamRegenerateTokenWrappedSchema = z.object({
  success: z.boolean(),
  data: z.object({
    access_token: z.string(),
    token_type: z.string().optional(),
    expires_in: z.number().optional(),
  }),
  message: z.string().optional(),
});

/**
 * Parse a `regenerate_access_token` response, accepting both the wrapped
 * format (observed in practice) and the flat format (per OpenAPI spec).
 * Returns a normalised `{ success, data, message }` shape in both cases.
 */
export function parseRegenerateTokenResponse(json: unknown) {
  const wrapped = UpstreamRegenerateTokenWrappedSchema.safeParse(json);
  if (wrapped.success) return wrapped.data;

  const flat = UpstreamRegenerateTokenFlatSchema.parse(json);
  return {
    success: true as const,
    data: flat,
    message: undefined,
  };
}
