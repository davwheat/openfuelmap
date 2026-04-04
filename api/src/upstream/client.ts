import {
  UPSTREAM_BASE_URL,
  UPSTREAM_PFS_PATH,
  UPSTREAM_FUEL_PRICES_PATH,
  UPSTREAM_REQUESTS_PER_MINUTE,
  USER_AGENT,
} from "../config";
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
  accessToken: string,
  effectiveStartTimestamp: string | null,
  parseResponse: (json: unknown) => T[],
): Promise<T[]> {
  const allResults: T[] = [];
  const delayMs = getRequestDelayMs();
  let lastRequestStart = 0;

  for (let batch = 1; ; batch++) {
    const url = new URL(basePath, UPSTREAM_BASE_URL);
    url.searchParams.set("batch-number", String(batch));
    if (effectiveStartTimestamp) {
      url.searchParams.set(
        "effective-start-timestamp",
        effectiveStartTimestamp,
      );
    }

    // Rate limit: wait between requests, accounting for time
    // already spent on the previous request
    if (lastRequestStart > 0) {
      const elapsed = Date.now() - lastRequestStart;
      const remaining = delayMs - elapsed;
      if (remaining > 0) {
        await delay(remaining);
      }
    }

    console.log(`GET ${url.toString()}`);
    lastRequestStart = Date.now();
    const response = await fetch(url.toString(), {
      headers: {
        Authorization: `Bearer ${accessToken}`,
        Accept: "application/json",
        "User-Agent": USER_AGENT,
      },
    });

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
  accessToken: string,
  since: string | null,
): Promise<UpstreamForecourt[]> {
  return fetchAllBatches(UPSTREAM_PFS_PATH, accessToken, since, (json) => {
    return UpstreamForecourtResponseSchema.parse(json);
  });
}

export async function fetchFuelPrices(
  accessToken: string,
  since: string | null,
): Promise<UpstreamFuelPriceStation[]> {
  return fetchAllBatches(
    UPSTREAM_FUEL_PRICES_PATH,
    accessToken,
    since,
    (json) => {
      return UpstreamFuelPriceResponseSchema.parse(json);
    },
  );
}
