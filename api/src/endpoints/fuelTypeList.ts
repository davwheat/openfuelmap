import { OpenAPIRoute } from "chanfana";
import { z } from "zod";
import { FUEL_TYPE_NAMES } from "../config";
import type { AppContext } from "../types";

const FuelTypeSchema = z.object({
  id: z.string().openapi({ example: "E10" }),
  name: z.string().openapi({ example: "Unleaded (E10)" }),
});

export class FuelTypeList extends OpenAPIRoute {
  schema = {
    tags: ["Reference"],
    summary: "List available fuel types",
    responses: {
      "200": {
        description:
          "Returns all fuel types currently offered by at least one forecourt",
        content: {
          "application/json": {
            schema: z.object({
              success: z.literal(true),
              result: z.object({
                fuel_types: FuelTypeSchema.array(),
              }),
            }),
          },
        },
      },
    },
  };

  async handle(c: AppContext) {
    const rows = await c.env.fuel_prices_db
      .prepare(
        `SELECT DISTINCT fuel_type FROM fuel_prices WHERE is_latest = 1 ORDER BY fuel_type`,
      )
      .all<{ fuel_type: string }>();

    const fuel_types = rows.results.map((row) => ({
      id: row.fuel_type,
      name: FUEL_TYPE_NAMES[row.fuel_type] ?? row.fuel_type,
    }));

    return {
      success: true,
      result: { fuel_types },
    };
  }
}
