import { OpenAPIRoute } from "chanfana";
import { z } from "zod";
import type { AppContext } from "../types";

const BrandSchema = z.object({
  name: z.string().openapi({ example: "Shell" }),
  forecourt_count: z.number().openapi({ example: 1042 }),
});

export class BrandList extends OpenAPIRoute {
  schema = {
    tags: ["Reference"],
    summary: "List available brands",
    responses: {
      "200": {
        description:
          "Returns all brands currently associated with at least one active forecourt",
        content: {
          "application/json": {
            schema: z.object({
              success: z.literal(true),
              result: z.object({
                brands: BrandSchema.array(),
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
        `SELECT brand_name, COUNT(*) as forecourt_count
         FROM forecourts
         WHERE is_active = 1
         GROUP BY brand_name
         ORDER BY brand_name`,
      )
      .all<{ brand_name: string; forecourt_count: number }>();

    const brands = rows.results.map((row) => ({
      name: row.brand_name,
      forecourt_count: row.forecourt_count,
    }));

    return {
      success: true,
      result: { brands },
    };
  }
}
