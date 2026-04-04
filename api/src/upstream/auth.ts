import { KV_OAUTH_TOKEN_KEY, UPSTREAM_TOKEN_URL, USER_AGENT } from "../config";
import { UpstreamTokenResponseSchema } from "./types";

export async function getAccessToken(
  kv: KVNamespace,
  clientId: string,
  clientSecret: string,
): Promise<string> {
  const cached = await kv.get(KV_OAUTH_TOKEN_KEY);
  if (cached) {
    return cached;
  }

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

  if (!response.ok) {
    const text = await response.text();
    throw new Error(`OAuth token request failed (${response.status}): ${text}`);
  }

  const json = await response.json();
  const parsed = UpstreamTokenResponseSchema.parse(json);

  if (!parsed.success) {
    throw new Error(
      `OAuth token request returned success=false: ${parsed.message}`,
    );
  }

  const expiresIn = parsed.data.expires_in ?? 3600;
  // Store with a TTL 60s shorter than the actual expiry to avoid using a stale token
  const ttl = Math.max(expiresIn - 60, 60);

  await kv.put(KV_OAUTH_TOKEN_KEY, parsed.data.access_token, {
    expirationTtl: ttl,
  });

  return parsed.data.access_token;
}
