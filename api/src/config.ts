/** Upstream fuel-finder API base URL */
export const UPSTREAM_BASE_URL = "https://www.fuel-finder.service.gov.uk";

/** OAuth2 token endpoints */
export const UPSTREAM_TOKEN_URL = `${UPSTREAM_BASE_URL}/api/v1/oauth/generate_access_token`;
export const UPSTREAM_REGENERATE_TOKEN_URL = `${UPSTREAM_BASE_URL}/api/v1/oauth/regenerate_access_token`;

/** Upstream API paths */
export const UPSTREAM_PFS_PATH = "/api/v1/pfs";
export const UPSTREAM_FUEL_PRICES_PATH = "/api/v1/pfs/fuel-prices";

/** User-Agent sent with all upstream requests */
export const USER_AGENT = "OpenFuelMapAPI (+https://github.com/davwheat)";

/**
 * Upstream rate limits (live environment):
 *  - 30 requests per minute per client
 *  - 1 concurrent request allowed per client
 *
 * We stay well under this with UPSTREAM_REQUESTS_PER_MINUTE.
 * Requests are issued sequentially (1 concurrent) with a delay
 * between each to stay within the per-minute budget.
 */
export const UPSTREAM_REQUESTS_PER_MINUTE = 25;

/** DB sync_meta keys */
export const SYNC_KEY_FORECOURTS = "last_forecourt_sync";
export const SYNC_KEY_PRICES = "last_price_sync";

/** KV key for cached OAuth token */
export const KV_OAUTH_TOKEN_KEY = "oauth_access_token";

/** KV key for cached OAuth refresh token */
export const KV_OAUTH_REFRESH_TOKEN_KEY = "oauth_refresh_token";

/**
 * TTL for the cached refresh token. The upstream API does not publish a
 * refresh-token lifetime; if it has been revoked sooner,
 * `regenerate_access_token` will fail and we fall back to
 * `generate_access_token`.
 */
export const OAUTH_REFRESH_TOKEN_TTL_SECONDS = 24 * 60 * 60;

/** Human-readable fuel type names */
export const FUEL_TYPE_NAMES: Record<string, string> = {
  B7_STANDARD: "Diesel",
  B7_PREMIUM: "Premium Diesel",
  E10: "Unleaded (E10)",
  E5: "Super Unleaded (E5)",
  HVO: "HVO Diesel",
  B10: "B10 Diesel",
};

