# Open Fuel Map -- Android

Shows UK fuel stations on a map with current prices, filtering, and historical price data.

## Modules

Uses api/impl separation per feature:

- `:feature:api` -- domain models, repository interfaces, nav keys (no Compose/Hilt deps)
- `:feature:data` -- repository impls, API clients, Hilt `@Binds` wiring
- `:feature:impl` -- Compose screens, ViewModels, navigation entry builders

```
:app                   MainActivity, theme, navigator, bottom nav
:app:api               INavigator, composition locals, shared chrome
:map:api / :map:impl   Map screen, station markers, detail sheet, filters
:list:api / :list:impl Forecourt list with search
:forecourts:api / :forecourts:data / :forecourts:impl
                       Station models, repo, detail UI
:stats:api / :stats:data / :stats:impl
                       Price stats and trends
:settings:api / :settings:impl
                       Preferences (fuel filters, units, theme)
:data                  Room, DataStore, OkHttp, NetworkBoundResource
:common:ui             Reusable Compose bits (Tag, Tooltip, etc.)
:common:nav            Nav utilities
:common:location       GPS/location services
```

Feature `:impl` modules depend on their own `:api` + `:data` plus shared modules. Never depend on
another feature's `:impl`.

## Setup

You need JDK 21 and Android SDK with compileSdk 37.

For release builds, also add signing config:

```properties
signing.storeFilePath=/path/to/keystore
signing.keyPassword=...
signing.storePassword=...
signing.keyAlias=android
```

## Building

```bash
./gradlew assembleDebug       # debug APK
./gradlew bundleRelease       # release AAB
./gradlew assembleRelease     # release APK
```

## Formatting

```bash
./gradlew spotlessApply       # auto-format
./gradlew spotlessCheck       # check only
```

Uses ktfmt for Kotlin, googleJavaFormat for Java, Eclipse WTP for XML.

## API

Talks to the Cloudflare Workers backend at `https://api.openfuelmap.co.uk` (hardcoded in `:data`'s
`ApiConstants`). OpenAPI schema is at `openapi.json`.

Data flows DB-first: `NetworkBoundResource` emits cached Room data, fetches from the API if stale,
saves the response, and Room re-emits the update.
