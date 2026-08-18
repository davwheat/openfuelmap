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
  computeFuelTypes,
  FUEL_TYPES_CACHE_KEY,
  FUEL_TYPES_CACHE_TTL_SECONDS,
  type FuelType,
} from "../cache/derived";
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
    const cached = await c.env.KV.get<FuelType[]>(FUEL_TYPES_CACHE_KEY, "json");
    if (cached) {
      return {
        success: true,
        result: { fuel_types: cached },
      };
    }

    // Fallback path: the sync normally keeps this warm.
    const fuel_types = await computeFuelTypes(c.env.fuel_prices_db);

    c.executionCtx.waitUntil(
      c.env.KV.put(FUEL_TYPES_CACHE_KEY, JSON.stringify(fuel_types), {
        expirationTtl: FUEL_TYPES_CACHE_TTL_SECONDS,
      }),
    );

    return {
      success: true,
      result: { fuel_types },
    };
  }
}
