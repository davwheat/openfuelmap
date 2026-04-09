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
