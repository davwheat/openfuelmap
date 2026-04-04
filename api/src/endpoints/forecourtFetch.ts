import { OpenAPIRoute } from "chanfana";
import { z } from "zod";
import { type AppContext, ForecourtSchema, FuelPriceSchema } from "../types";

export class ForecourtFetch extends OpenAPIRoute {
  schema = {
    tags: ["Forecourts"],
    summary: "Get a single forecourt by node ID",
    request: {
      params: z.object({
        nodeId: z.string().describe("Forecourt node_id"),
      }),
    },
    responses: {
      "200": {
        description: "Returns the forecourt and its current prices",
        content: {
          "application/json": {
            schema: z.object({
              success: z.literal(true),
              result: z.object({
                forecourt: ForecourtSchema,
                current_prices: FuelPriceSchema.array(),
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

    const row = (await c.env.fuel_prices_db
      .prepare("SELECT * FROM forecourts WHERE node_id = ? AND is_active = 1")
      .bind(nodeId)
      .first()) as Record<string, unknown> | null;

    if (!row) {
      return Response.json(
        { success: false, error: "Forecourt not found" },
        { status: 404 },
      );
    }

    const prices = await c.env.fuel_prices_db
      .prepare(
        `SELECT fuel_type, price, price_last_updated, price_change_effective_timestamp
			 FROM fuel_prices
			 WHERE node_id = ? AND is_latest = 1
			 ORDER BY fuel_type`,
      )
      .bind(nodeId)
      .all();

    const forecourt = {
      node_id: row.node_id,
      trading_name: row.trading_name,
      brand_name: row.brand_name,
      is_same_trading_and_brand_name: Boolean(
        row.is_same_trading_and_brand_name,
      ),
      public_phone_number: row.public_phone_number,
      temporary_closure: Boolean(row.temporary_closure),
      permanent_closure:
        row.permanent_closure === null ? null : Boolean(row.permanent_closure),
      permanent_closure_date: row.permanent_closure_date,
      is_motorway_service_station: Boolean(row.is_motorway_service_station),
      is_supermarket_service_station: Boolean(
        row.is_supermarket_service_station,
      ),
      location: {
        address_line_1: row.address_line_1,
        address_line_2: row.address_line_2,
        city: row.city,
        country: row.country,
        county: row.county,
        postcode: row.postcode,
        latitude: row.latitude,
        longitude: row.longitude,
      },
      amenities: JSON.parse(row.amenities as string),
      opening_times: JSON.parse(row.opening_times as string),
      fuel_types: JSON.parse(row.fuel_types as string),
      updated_at: row.updated_at,
    };

    return {
      success: true,
      result: {
        forecourt,
        current_prices: prices.results,
      },
    };
  }
}
