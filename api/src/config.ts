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

/**
 * Generous UK bounding box covering GB mainland, Northern Ireland, the Isle
 * of Man, the Channel Islands, Shetland and the Isles of Scilly. Used as a
 * sanity check on incoming forecourt coordinates — the upstream feed should
 * only contain UK sites, so anything outside this box is a data issue worth
 * logging.
 */
export const UK_BBOX = {
  minLat: 49.0,
  maxLat: 61.0,
  minLon: -9.0,
  maxLon: 2.0,
} as const;

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
