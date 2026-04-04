import { fromHono } from "chanfana";
import { Hono } from "hono";
import { handleScheduled } from "./cron/handler";
import { ForecourtFetch } from "./endpoints/forecourtFetch";
import { ForecourtList } from "./endpoints/forecourtList";
import { PriceHistory } from "./endpoints/priceHistory";
import { FuelTypeList } from "./endpoints/fuelTypeList";
import { PriceList } from "./endpoints/priceList";

const app = new Hono<{ Bindings: Env }>();

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

export default {
  fetch: app.fetch,
  async scheduled(_event: ScheduledEvent, env: Env, _ctx: ExecutionContext) {
    await handleScheduled(env);
  },
};
