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

import {
  UPSTREAM_BASE_URL,
  UPSTREAM_PFS_PATH,
  UPSTREAM_FUEL_PRICES_PATH,
  UPSTREAM_REQUESTS_PER_MINUTE,
  USER_AGENT,
} from "../config";
import type { AccessTokenProvider } from "./auth";
import {
  UpstreamForecourtResponseSchema,
  UpstreamFuelPriceResponseSchema,
  type UpstreamForecourt,
  type UpstreamFuelPriceStation,
} from "./types";

const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * Returns the minimum delay in ms between requests to stay within
 * the configured requests-per-minute budget.
 */
function getRequestDelayMs(): number {
  return Math.ceil(60_000 / UPSTREAM_REQUESTS_PER_MINUTE);
}

/**
 * Generic paginated fetch. Loops batch-number from 1 upward until an
 * empty data array is returned. Delays between requests to respect
 * the upstream rate limit (1 concurrent, N req/min).
 */
async function fetchAllBatches<T>(
  basePath: string,
  tokenProvider: AccessTokenProvider,
  effectiveStartTimestamp: string | null,
  parseResponse: (json: unknown) => T[],
): Promise<T[]> {
  const allResults: T[] = [];
  const delayMs = getRequestDelayMs();
  let lastRequestStart = 0;
  let accessToken = await tokenProvider.get();

  const rateLimit = async () => {
    if (lastRequestStart > 0) {
      const elapsed = Date.now() - lastRequestStart;
      const remaining = delayMs - elapsed;
      if (remaining > 0) {
        await delay(remaining);
      }
    }
  };

  for (let batch = 1; ; batch++) {
    const url = new URL(basePath, UPSTREAM_BASE_URL);
    url.searchParams.set("batch-number", String(batch));
    if (effectiveStartTimestamp) {
      url.searchParams.set(
        "effective-start-timestamp",
        effectiveStartTimestamp,
      );
    }

    const doRequest = async () => {
      await rateLimit();
      console.log(`GET ${url.toString()}`);
      lastRequestStart = Date.now();
      return fetch(url.toString(), {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
          "User-Agent": USER_AGENT,
        },
      });
    };

    let response = await doRequest();

    // 403 usually means the token has been revoked/expired server-side
    // since we cached it. Refresh once and retry the same batch before
    // bailing — a persistent 403 after refresh is a real auth failure.
    if (response.status === 403) {
      console.warn(
        `[upstream] Batch ${batch} returned 403; refreshing access token and retrying once`,
      );
      accessToken = await tokenProvider.refresh();
      response = await doRequest();
    }

    // 404 means no more batches available
    if (response.status === 404) {
      console.log(`[upstream] Batch ${batch} returned 404, no more data`);
      break;
    }

    if (!response.ok) {
      const text = await response.text();
      throw new Error(
        `Upstream API error ${response.status} on ${url.pathname}?${url.searchParams}: ${text}`,
      );
    }

    const json = await response.json();
    const items = parseResponse(json);

    console.log(`[upstream] Batch ${batch}: ${items.length} entries`);

    if (items.length === 0) {
      break;
    }

    allResults.push(...items);
  }

  return allResults;
}

export async function fetchForecourts(
  tokenProvider: AccessTokenProvider,
  since: string | null,
): Promise<UpstreamForecourt[]> {
  return fetchAllBatches(UPSTREAM_PFS_PATH, tokenProvider, since, (json) => {
    return UpstreamForecourtResponseSchema.parse(json);
  });
}

export async function fetchFuelPrices(
  tokenProvider: AccessTokenProvider,
  since: string | null,
): Promise<UpstreamFuelPriceStation[]> {
  return fetchAllBatches(
    UPSTREAM_FUEL_PRICES_PATH,
    tokenProvider,
    since,
    (json) => {
      return UpstreamFuelPriceResponseSchema.parse(json);
    },
  );
}
