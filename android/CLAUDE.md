# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & tooling

Gradle wrapper at repo root. Java 11 source/target, Kotlin 2.3, AGP 9.2 alpha, compileSdk 36, minSdk 26. AGP and other plugin versions are cutting-edge alphas — unfamiliar APIs may genuinely be the correct ones.

- `./gradlew assembleDebug` — build debug APK
- `./gradlew :map:impl:testDebugUnitTest` — run unit tests for a single module
- `./gradlew spotlessApply` — format all Kotlin/Java/XML (ktfmt kotlinlangStyle 0.62, googleJavaFormat 1.35, Eclipse WTP for XML using `xml.prefs`)
- `./gradlew spotlessCheck` — verify formatting

`MAPS_API_KEY` must be set in `local.properties` (gitignored); it is injected into the manifest via the secrets-gradle-plugin. `appBuildNumber` is auto-generated from the current date+time on every build (see `build.gradle.kts` root). The API base URL is hardcoded to `https://api.openfuelmap.co.uk` in `:data`'s `ApiConstants`.

## Module architecture

The project uses an **api/impl separation pattern** per feature. Feature modules come in three flavours:

- `:<feature>:api` — public contracts only: domain models, repository *interfaces*, nav keys, sealed result types. No Android/Compose/Hilt dependencies beyond what's strictly needed. Other feature modules depend on `:api`, never on `:impl`.
- `:<feature>:data` — repository *implementations*, API clients (OkHttp + kotlinx.serialization), DTOs, Hilt `@Binds` modules wiring impls to interfaces.
- `:<feature>:impl` — UI (Compose screens, sheets), ViewModels, and the `EntryProviderScope` extension function that wires the feature into navigation (e.g. `mapEntryBuilder()`).

Current feature: `:map` (Home map screen with station markers, detail sheet, filter sheet, fuel-type selector).

Shared modules:
- `:app` — single `MainActivity`, `OpenFuelMapApplication` (`@HiltAndroidApp`), `Navigator` implementation, Compose theme. Aggregates all feature modules.
- `:app:api` — app-wide composition locals (`LocalNavigator`, `LocalBottomNavBarProvider`), the `INavigator` interface, shared chrome like `BottomNavBar`. Any module can depend on this to reach the navigator without depending on `:app`.
- `:common:ui` — reusable Compose components (`Tag`, `SimpleTooltip`, view extensions). No feature knowledge.
- `:data` — app-wide data layer: Room (`AppDatabase`, `FuelTypeDao`, single `FuelTypeEntity`), DataStore preferences (`UserPreferencesRepository`), Hilt `DataModule` providing `OkHttpClient`, `Json`, and Room instances, plus `NetworkBoundResource` (DB-first Flow pattern: emit cached → fetch if stale → save → DB re-emits).

**Dependency direction**: `:app` → all feature modules; feature `:impl` → feature `:api` + feature `:data` + `:data` + `:common:ui`; feature `:data` → feature `:api` + `:data`. Never depend on another feature's `:impl`.

## Navigation

Uses **androidx.navigation3** (alpha) rather than Compose Navigation. A custom `NavigationState` / `rememberNavigationState` (in `:app`, package `dev.davwheat.smartpromptpilot.nav` — legacy namespace from a template) manages multiple top-level back stacks keyed by `NavKey`, surviving config changes and process death via `rememberSerializable`. `Navigator` (implementing `:app:api`'s `INavigator`) mutates this state. Feature modules expose nav routes as `@Serializable data object`/`data class` implementing `NavKey` in their `:api` module (e.g. `MapNav.Home`), and expose an `EntryProviderScope<NavKey>.xxxEntryBuilder()` extension from `:impl` that `MainActivity` composes into a single `entryProvider { … }`.

## Dependency injection

Hilt throughout. KSP-based. Every module that participates in DI applies the hilt plugin. Repository interfaces live in `:api`, implementations in `:data`, bound via `@Binds` in a `@Module @InstallIn(SingletonComponent::class)` class in that feature's `:data`. ViewModels use `@HiltViewModel` + `hiltViewModel()` inside entry builders. The app also uses `HiltWorkerFactory` for WorkManager integration (WorkManager's default initializer is disabled in the manifest).

## Networking & results

OkHttp + `kotlinx.serialization.json` (configured with `ignoreUnknownKeys`, `isLenient`, `coerceInputValues`). API clients return `ApiResult<T>` (sealed: `Success` | `ApiError` | `NetworkError`) — never throw across the boundary. Callers pattern-match; ViewModels convert failures into observable error state. The Cloudflare Workers API lives at `../api/` (sibling to `android/`) and its OpenAPI schema is at `android/openapi.json`.

## Code style notes

- ktfmt kotlinlangStyle enforces 4-space indent and particular wrapping rules. Run spotless before committing.
- `javax.inject` annotations (`@Inject`, `@Singleton`, `@Named`) are used — not Dagger's own.
- Composables at module boundaries receive `viewModel: XxxViewModel` directly; `hiltViewModel()` is called in the entry builder, not inside the screen.
