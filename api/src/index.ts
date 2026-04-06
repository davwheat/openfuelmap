import { fromHono } from "chanfana";
import { Hono } from "hono";
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
