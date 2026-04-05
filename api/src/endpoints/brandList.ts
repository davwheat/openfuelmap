import { OpenAPIRoute } from "chanfana";
import { z } from "zod";
import type { AppContext } from "../types";

const BrandSchema = z.object({
  name: z.string().openapi({ example: "Shell" }),
  forecourt_count: z.number().openapi({ example: 1042 }),
});

type Brand = { name: string; forecourt_count: number };

const BRANDS_CACHE_KEY = "brands:v1";
const BRANDS_CACHE_TTL_SECONDS = 8 * 60 * 60;

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
    const cached = await c.env.KV.get<Brand[]>(BRANDS_CACHE_KEY, "json");
    if (cached) {
      return {
        success: true,
        result: { brands: cached },
      };
    }

    const rows = await c.env.fuel_prices_db
      .prepare(
        `SELECT brand_name, COUNT(*) as forecourt_count
         FROM forecourts
         WHERE is_active = 1
         GROUP BY brand_name
         ORDER BY brand_name`,
      )
      .all<{ brand_name: string; forecourt_count: number }>();

    const brands: Brand[] = rows.results.map((row) => ({
      name: row.brand_name,
      forecourt_count: row.forecourt_count,
    }));

    await c.env.KV.put(BRANDS_CACHE_KEY, JSON.stringify(brands), {
      expirationTtl: BRANDS_CACHE_TTL_SECONDS,
    });

    return {
      success: true,
      result: { brands },
    };
  }
}
