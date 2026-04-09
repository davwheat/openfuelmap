import {
  KV_OAUTH_REFRESH_TOKEN_KEY,
  KV_OAUTH_TOKEN_KEY,
  OAUTH_REFRESH_TOKEN_TTL_SECONDS,
  UPSTREAM_REGENERATE_TOKEN_URL,
  UPSTREAM_TOKEN_URL,
  USER_AGENT,
} from "../config";
import {
  parseRegenerateTokenResponse,
  UpstreamTokenResponseSchema,
} from "./types";

/** TTL buffer: cache access tokens with an expiry shorter than their real one. */
const ACCESS_TOKEN_TTL_BUFFER_SECONDS = 60;
const DEFAULT_ACCESS_TOKEN_EXPIRES_IN = 3600;
const MIN_ACCESS_TOKEN_TTL_SECONDS = 60;

/** TTL buffer applied to refresh token expiry extracted from the JWT. */
const REFRESH_TOKEN_TTL_BUFFER_SECONDS = 5 * 60;
/** Cloudflare KV requires expirationTtl >= 60s. */
const MIN_KV_TTL_SECONDS = 60;

function computeAccessTokenTtl(expiresIn: number | undefined): number {
  const effective = expiresIn ?? DEFAULT_ACCESS_TOKEN_EXPIRES_IN;
  return Math.max(
    effective - ACCESS_TOKEN_TTL_BUFFER_SECONDS,
    MIN_ACCESS_TOKEN_TTL_SECONDS,
  );
}

/**
 * Decode the `exp` claim (Unix seconds) from a JWT without verifying the
 * signature. We only need the expiry — the token itself is what we send to
 * the upstream, so its authenticity is the upstream's problem.
 */
function jwtExpirySeconds(jwt: string): number | null {
  const parts = jwt.split(".");
  if (parts.length !== 3) return null;
  try {
    const payload = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const padded = payload + "=".repeat((4 - (payload.length % 4)) % 4);
    const parsed = JSON.parse(atob(padded)) as { exp?: unknown };
    return typeof parsed.exp === "number" ? parsed.exp : null;
  } catch {
    return null;
  }
}

/**
 * Compute a KV TTL for a refresh token by extracting `exp` from the JWT and
 * subtracting a safety buffer. Falls back to the configured default if the
 * token doesn't parse as a JWT or lacks an `exp` claim. Returns `null` if
 * the token is already expired (caller should skip caching).
 */
function computeRefreshTokenTtl(refreshToken: string): number | null {
  const exp = jwtExpirySeconds(refreshToken);
  if (exp == null) {
    console.warn(
      "Refresh token is not a parseable JWT or lacks `exp`; using fallback TTL.",
    );
    return OAUTH_REFRESH_TOKEN_TTL_SECONDS;
  }
  const nowSeconds = Math.floor(Date.now() / 1000);
  const ttl = exp - nowSeconds - REFRESH_TOKEN_TTL_BUFFER_SECONDS;
  if (ttl < MIN_KV_TTL_SECONDS) return null;
  return ttl;
}

export async function getAccessToken(
  kv: KVNamespace,
  clientId: string,
  clientSecret: string,
  disableRefresh = false,
): Promise<string> {
  if (disableRefresh) {
    return generateAccessToken(kv, clientId, clientSecret);
  }

  const cached = await kv.get(KV_OAUTH_TOKEN_KEY);
  if (cached) {
    return cached;
  }

  // Try the cheap path first: exchange a cached refresh token for a new
  // access token. This avoids sending the client_secret on every rotation.
  const refreshToken = await kv.get(KV_OAUTH_REFRESH_TOKEN_KEY);
  if (refreshToken) {
    const regenerated = await tryRegenerateAccessToken(
      kv,
      clientId,
      refreshToken,
    );
    if (regenerated) {
      return regenerated;
    }
  }

  return generateAccessToken(kv, clientId, clientSecret);
}

/**
 * Stateful token provider passed to upstream clients so they can refresh
 * the access token after a 403 without knowing about KV or credentials.
 */
export interface AccessTokenProvider {
  /** Returns the current access token, fetching one if not cached. */
  get(): Promise<string>;
  /** Evicts the cached access token and returns a fresh one. */
  refresh(): Promise<string>;
}

export function createAccessTokenProvider(
  kv: KVNamespace,
  clientId: string,
  clientSecret: string,
  opts: { disableRefresh?: boolean; disableCache?: boolean } = {},
): AccessTokenProvider {
  if (opts.disableCache) {
    return {
      get: () => fetchAccessToken(clientId, clientSecret),
      refresh: () => fetchAccessToken(clientId, clientSecret),
    };
  }
  return {
    get: () =>
      getAccessToken(kv, clientId, clientSecret, opts.disableRefresh ?? false),
    refresh: async () => {
      await kv.delete(KV_OAUTH_TOKEN_KEY);
      return getAccessToken(
        kv,
        clientId,
        clientSecret,
        opts.disableRefresh ?? false,
      );
    },
  };
}

/**
 * Fetch a new access token from the upstream OAuth endpoint. Returns the
 * parsed response (access token, optional refresh token, expiry).
 */
async function fetchAccessToken(
  clientId: string,
  clientSecret: string,
): Promise<string> {
  console.log(`POST ${UPSTREAM_TOKEN_URL}`);
  const response = await fetch(UPSTREAM_TOKEN_URL, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
      "User-Agent": USER_AGENT,
    },
    body: JSON.stringify({
      client_id: clientId,
      client_secret: clientSecret,
    }),
  });

  const text = await response.text();
  const headers = Object.fromEntries(response.headers.entries());
  console.log(
    `[oauth] generate_access_token response (${response.status}):`,
    text,
  );
  console.log(
    `[oauth] generate_access_token response headers:`,
    JSON.stringify(headers),
  );

  if (!response.ok) {
    throw new Error(
      `OAuth token request failed (${response.status}): ${text || "(no body)"}`,
    );
  }

  const json = JSON.parse(text);
  const parsed = UpstreamTokenResponseSchema.parse(json);

  if (!parsed.success) {
    throw new Error(
      `OAuth token request returned success=false: ${parsed.message}`,
    );
  }

  return parsed.data.access_token;
}

/**
 * Fetch a new access token and cache it (along with any refresh token) in KV.
 */
async function generateAccessToken(
  kv: KVNamespace,
  clientId: string,
  clientSecret: string,
): Promise<string> {
  console.log(`POST ${UPSTREAM_TOKEN_URL}`);
  const response = await fetch(UPSTREAM_TOKEN_URL, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
      "User-Agent": USER_AGENT,
    },
    body: JSON.stringify({
      client_id: clientId,
      client_secret: clientSecret,
    }),
  });

  const text = await response.text();
  const headers = Object.fromEntries(response.headers.entries());
  console.log(
    `[oauth] generate_access_token response (${response.status}):`,
    text,
  );
  console.log(
    `[oauth] generate_access_token response headers:`,
    JSON.stringify(headers),
  );

  if (!response.ok) {
    throw new Error(
      `OAuth token request failed (${response.status}): ${text || "(no body)"}`,
    );
  }

  const json = JSON.parse(text);
  const parsed = UpstreamTokenResponseSchema.parse(json);

  if (!parsed.success) {
    throw new Error(
      `OAuth token request returned success=false: ${parsed.message}`,
    );
  }

  const ttl = computeAccessTokenTtl(parsed.data.expires_in);

  await kv.put(KV_OAUTH_TOKEN_KEY, parsed.data.access_token, {
    expirationTtl: ttl,
  });

  if (parsed.data.refresh_token) {
    const refreshTtl = computeRefreshTokenTtl(parsed.data.refresh_token);
    if (refreshTtl != null) {
      await kv.put(KV_OAUTH_REFRESH_TOKEN_KEY, parsed.data.refresh_token, {
        expirationTtl: refreshTtl,
      });
    } else {
      console.warn(
        "Refresh token is already expired or expires too soon; not caching.",
      );
    }
  }

  return parsed.data.access_token;
}

/**
 * Attempt to regenerate an access token using a refresh token. Returns the
 * new access token on success, or `null` if the refresh path failed (in
 * which case the caller should fall back to a full `generate_access_token`
 * exchange). Stale refresh tokens are evicted from KV on failure so we
 * don't keep retrying them on subsequent invocations.
 */
async function tryRegenerateAccessToken(
  kv: KVNamespace,
  clientId: string,
  refreshToken: string,
): Promise<string | null> {
  console.log(`POST ${UPSTREAM_REGENERATE_TOKEN_URL}`);
  let response: Response;
  try {
    response = await fetch(UPSTREAM_REGENERATE_TOKEN_URL, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
        "User-Agent": USER_AGENT,
      },
      body: JSON.stringify({
        client_id: clientId,
        refresh_token: refreshToken,
      }),
    });
  } catch (err) {
    console.warn(
      `OAuth refresh network error, falling back to full auth: ${err}`,
    );
    return null;
  }

  const text = await response.text();
  const headers = Object.fromEntries(response.headers.entries());
  console.log(
    `[oauth] regenerate_access_token response (${response.status}):`,
    text,
  );
  console.log(
    `[oauth] regenerate_access_token response headers:`,
    JSON.stringify(headers),
  );

  if (!response.ok) {
    console.warn(
      `OAuth refresh failed (${response.status}): ${text || "(no body)"}. Falling back to full auth.`,
    );
    // 400 means invalid refresh_token or client_id; 401/403 mean the
    // refresh token is no longer usable. Clear it so we don't keep
    // hitting this endpoint on every call.
    if (
      response.status === 400 ||
      response.status === 401 ||
      response.status === 403
    ) {
      await kv.delete(KV_OAUTH_REFRESH_TOKEN_KEY);
    }
    return null;
  }

  const json = JSON.parse(text);
  let parsed: ReturnType<typeof parseRegenerateTokenResponse>;
  try {
    parsed = parseRegenerateTokenResponse(json);
  } catch (err) {
    console.warn(
      `OAuth refresh response parse failed, falling back to full auth: ${err}`,
    );
    await kv.delete(KV_OAUTH_REFRESH_TOKEN_KEY);
    return null;
  }

  if (!parsed.success) {
    console.warn(
      `OAuth refresh returned success=false: ${parsed.message}. Falling back to full auth.`,
    );
    await kv.delete(KV_OAUTH_REFRESH_TOKEN_KEY);
    return null;
  }

  const ttl = computeAccessTokenTtl(parsed.data.expires_in);
  await kv.put(KV_OAUTH_TOKEN_KEY, parsed.data.access_token, {
    expirationTtl: ttl,
  });

  return parsed.data.access_token;
}
