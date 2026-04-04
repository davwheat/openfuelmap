# UK Fuel Prices API

A Cloudflare Worker that ingests fuel price and forecourt data from the UK government's [Fuel Finder](https://www.fuel-finder.service.gov.uk) API, stores it in D1, and serves it via a REST API with OpenAPI documentation.

Built with [Hono](https://hono.dev), [chanfana](https://chanfana.pages.dev), and [Zod](https://zod.dev).

## API Endpoints

| Route                                        | Description                                                              |
| -------------------------------------------- | ------------------------------------------------------------------------ |
| `GET /`                                      | OpenAPI / Swagger documentation                                          |
| `GET /api/forecourts`                        | List forecourts (filterable by brand, postcode, fuel type, bounding box) |
| `GET /api/forecourts/:nodeId`                | Get a single forecourt with current prices                               |
| `GET /api/prices`                            | List latest fuel prices across stations (sortable, filterable)           |
| `GET /api/forecourts/:nodeId/prices/history` | Historic price data for a forecourt                                      |

See [api.http](api.http) for example requests.

## Data Sync

A cron trigger runs every 10 minutes to fetch new data from the upstream API:

1. Authenticates via OAuth2 (token cached in KV)
2. Syncs forecourt metadata (reference data first)
3. Syncs fuel prices (with orphan detection for missing forecourts)

Incremental updates use `effective-start-timestamp` to only fetch changes since the last sync. Sync timestamps are stored in KV.

## Setup

```bash
pnpm install

# Create the D1 database and KV namespace
pnpm wrangler d1 create fuel-prices-db
pnpm wrangler kv namespace create KV

# Update wrangler.jsonc with the returned IDs

# Set upstream API credentials
pnpm wrangler secret put UPSTREAM_CLIENT_ID
pnpm wrangler secret put UPSTREAM_CLIENT_SECRET

# Apply the D1 migration
pnpm wrangler d1 migrations apply fuel-prices-db --local   # for dev
pnpm wrangler d1 migrations apply fuel-prices-db --remote  # for prod

# Regenerate types
pnpm wrangler types
```

## Development

```bash
pnpm dev
```

- Visit `http://localhost:8787/` for the Swagger UI
- Trigger a cron sync manually: `curl http://localhost:8787/__scheduled`

## Deployment

```bash
pnpm deploy
```

## Project Structure

```
src/
  index.ts              Entry point (routes + scheduled export)
  config.ts             Top-level constants
  types.ts              Zod schemas for API responses
  upstream/             Upstream API client (OAuth, paginated fetching)
  cron/                 Cron sync logic (handler, forecourts, prices)
  db/                   D1 database layer (upserts, sync metadata)
  endpoints/            API endpoint handlers
migrations/             D1 schema migrations
```
