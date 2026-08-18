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

import { fromHono } from "chanfana";
import { Hono } from "hono";
import { cache } from "hono/cache";
import { handleScheduled } from "./cron/handler";
import {
  adminCronForecourts,
  adminCronPrices,
  adminCronRun,
} from "./endpoints/adminCron";
import { BrandList } from "./endpoints/brandList";
import { DailyMedianPrices } from "./endpoints/dailyMedianPrices";
import { ForecourtFetch } from "./endpoints/forecourtFetch";
import { ForecourtList } from "./endpoints/forecourtList";
import { PriceHistory } from "./endpoints/priceHistory";
import { FuelTypeList } from "./endpoints/fuelTypeList";
import { PriceList } from "./endpoints/priceList";

const app = new Hono<{ Bindings: Env }>();

// Undocumented admin endpoints for manually triggering the cron job.
// Registered on the raw Hono app (not the chanfana openapi wrapper) so they
// do not appear in the OpenAPI schema or docs page. Guarded by a shared
// secret via Authorization: Bearer <ADMIN_SECRET>.
app.post("/admin/cron/run", adminCronRun);
app.post("/admin/cron/forecourts", adminCronForecourts);
app.post("/admin/cron/prices", adminCronPrices);

/**
 * Responses are rebuilt by the sync roughly every 20 minutes, so serving a
 * few minutes stale costs nothing and keeps repeat requests -- a map that
 * pans back over ground it has already covered, an app relaunch -- off the
 * database entirely. The same headers let the client's own HTTP cache skip
 * the request altogether.
 *
 * Registered per route rather than as one wildcard so each can carry a TTL
 * matched to how often its data actually moves. The patterns do not overlap,
 * so exactly one applies to any request.
 */
const CACHE_NAME = "openfuelmap-api";

const cacheFor = (maxAgeSeconds: number) =>
  cache({
    cacheName: CACHE_NAME,
    cacheControl: `public, max-age=${maxAgeSeconds}`,
  });

const PRICE_DATA_MAX_AGE = 5 * 60;
const HISTORY_MAX_AGE = 30 * 60;
const STATS_MAX_AGE = 60 * 60;
const REFERENCE_DATA_MAX_AGE = 24 * 60 * 60;

app.get("/api/forecourts", cacheFor(PRICE_DATA_MAX_AGE));
app.get("/api/forecourts/:nodeId", cacheFor(PRICE_DATA_MAX_AGE));
app.get("/api/forecourts/:nodeId/prices/history", cacheFor(HISTORY_MAX_AGE));
app.get("/api/prices", cacheFor(PRICE_DATA_MAX_AGE));
app.get("/api/fuel-types", cacheFor(REFERENCE_DATA_MAX_AGE));
app.get("/api/brands", cacheFor(REFERENCE_DATA_MAX_AGE));
app.get("/api/stats/daily-median-prices", cacheFor(STATS_MAX_AGE));

const openapi = fromHono(app, {
  docs_url: "/",
  schema: {
    info: {
      title: "UK Fuel Prices API",
      version: "1.0.0",
      description:
        "REST API serving UK government fuel price data from fuel-finder.service.gov.uk",
    },
  },
});

openapi.get("/api/forecourts", ForecourtList);
openapi.get("/api/forecourts/:nodeId", ForecourtFetch);
openapi.get("/api/prices", PriceList);
openapi.get("/api/forecourts/:nodeId/prices/history", PriceHistory);
openapi.get("/api/fuel-types", FuelTypeList);
openapi.get("/api/brands", BrandList);
openapi.get("/api/stats/daily-median-prices", DailyMedianPrices);

export default {
  fetch: app.fetch,
  async scheduled(_event: ScheduledEvent, env: Env, _ctx: ExecutionContext) {
    await handleScheduled(env);
  },
};
