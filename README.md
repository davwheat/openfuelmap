# Open Fuel Map

Shows UK fuel prices on a map. Pulls data from the government's [Fuel Finder](https://www.fuel-finder.service.gov.uk) API every 30 minutes, stores price history, and makes it browsable via an Android app.

## How it works

A Cloudflare Worker syncs forecourt metadata and fuel prices from the upstream API on a cron schedule, storing everything in D1.

The Android app hits the Worker's REST API, caches reference data locally in Room, and shows stations on a Google Maps view with filtering, search, and price history.

## Repo layout

```
android/     Kotlin/Compose Android app
api/         Cloudflare Workers REST API (TypeScript, Hono)
.github/     CI — format checks, APK/AAB builds
```

See [api/README.md](api/README.md) and [android/README.md](android/README.md) for setup.

