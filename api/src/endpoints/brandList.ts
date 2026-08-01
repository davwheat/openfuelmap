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
  BRANDS_CACHE_KEY,
  BRANDS_CACHE_TTL_SECONDS,
  computeBrands,
  type Brand,
} from "../cache/derived";
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
    const cached = await c.env.KV.get<Brand[]>(BRANDS_CACHE_KEY, "json");
    if (cached) {
      return {
        success: true,
        result: { brands: cached },
      };
    }

    // Fallback path: the sync normally keeps this warm.
    const brands: Brand[] = await computeBrands(c.env.fuel_prices_db);

    c.executionCtx.waitUntil(
      c.env.KV.put(BRANDS_CACHE_KEY, JSON.stringify(brands), {
        expirationTtl: BRANDS_CACHE_TTL_SECONDS,
      }),
    );

    return {
      success: true,
      result: { brands },
    };
  }
}
