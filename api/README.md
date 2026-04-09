# Open Fuel Map -- API

Cloudflare Worker that pulls fuel price and forecourt data from the UK government's [Fuel Finder](https://www.fuel-finder.service.gov.uk) API, stores it in D1, and serves it as a REST API. Built with [Hono](https://hono.dev), [chanfana](https://chanfana.pages.dev), and [Zod](https://zod.dev).

## Endpoints

| Route                                        | What it does                                                       |
| -------------------------------------------- | ------------------------------------------------------------------ |
| `GET /`                                      | Swagger/OpenAPI docs                                               |
| `GET /api/forecourts`                        | List stations (filter by brand, postcode, fuel type, bounding box) |
| `GET /api/forecourts/:nodeId`                | Single station with current prices                                 |
| `GET /api/prices`                            | Latest prices across stations (sortable, filterable)               |
| `GET /api/forecourts/:nodeId/prices/history` | Price history for a station                                        |
| `GET /api/fuel-types`                        | Available fuel types                                               |
| `GET /api/brands`                            | Fuel brand list                                                    |
| `GET /api/stats/daily-median-prices`         | Aggregate price stats                                              |

See [api.http](api.http) for example requests.

## Data sync

A cron trigger runs every 30 minutes:

1. Authenticates with the upstream API via OAuth2 (token cached in KV)
2. Syncs forecourt metadata (reference data first)
3. Syncs fuel prices (detects orphans for missing forecourts)

Uses `effective-start-timestamp` for incremental updates -- only fetches changes since the last sync. Timestamps stored in KV.

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

Swagger UI at `http://localhost:8787/`. Trigger a manual sync with `curl http://localhost:8787/__scheduled`.

## Deployment

```bash
pnpm deploy
```

Deploys to `api.openfuelmap.co.uk`.

## Project structure

```
src/
  index.ts              Entry point (routes + scheduled export)
  config.ts             Constants (upstream URLs, rate limits, credentials)
  types.ts              Zod schemas for API validation
  brands.ts             Fuel brand metadata
  upstream/             Upstream API client (OAuth, paginated fetching, rate limiting)
  cron/                 Cron sync logic (forecourts + prices)
  db/                   D1 layer (upserts, sync metadata)
  endpoints/            Route handlers
migrations/             D1 schema migrations
```
