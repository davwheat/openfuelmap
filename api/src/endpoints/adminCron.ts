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

import type { Context } from "hono";
import { refreshDerivedCaches } from "../cache/derived";
import { handleScheduled } from "../cron/handler";
import { syncForecourts } from "../cron/syncForecourts";
import { syncPrices } from "../cron/syncPrices";
import { createAccessTokenProvider } from "../upstream/auth";

type AdminContext = Context<{ Bindings: Env }>;

/**
 * Constant-time string equality. Prevents timing attacks on the shared
 * secret — overkill over the public internet, but trivial to do right.
 */
function constantTimeEquals(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) {
    diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
  return diff === 0;
}

function isAuthorized(c: AdminContext): boolean {
  const expected = c.env.ADMIN_SECRET;
  if (!expected) return false;
  const header = c.req.header("Authorization") ?? "";
  const match = header.match(/^Bearer\s+(.+)$/i);
  if (!match) return false;
  return constantTimeEquals(match[1]!, expected);
}

function unauthorized(c: AdminContext) {
  return c.json({ error: "unauthorized" }, 401);
}

function errorResponse(c: AdminContext, err: unknown) {
  const message = err instanceof Error ? err.message : String(err);
  return c.json({ error: "sync_failed", message }, 500);
}

/** Runs the full sync (forecourts then prices). */
export async function adminCronRun(c: AdminContext) {
  if (!isAuthorized(c)) return unauthorized(c);
  try {
    const result = await handleScheduled(c.env);
    return c.json({ status: "ok", ...result });
  } catch (err) {
    return errorResponse(c, err);
  }
}

/** Runs only the forecourts sync. */
export async function adminCronForecourts(c: AdminContext) {
  if (!isAuthorized(c)) return unauthorized(c);
  const start = Date.now();
  try {
    const tokenProvider = createAccessTokenProvider(
      c.env.KV,
      c.env.UPSTREAM_CLIENT_ID,
      c.env.UPSTREAM_CLIENT_SECRET,
      {
        disableRefresh: c.env.DISABLE_OAUTH_REFRESH === true,
        disableCache: c.env.DISABLE_OAUTH_CACHE === true,
      },
    );
    const result = await syncForecourts(
      c.env.fuel_prices_db,
      c.env.KV,
      tokenProvider,
    );
    const caches = await refreshDerivedCaches(c.env.fuel_prices_db, c.env.KV);
    return c.json({
      status: "ok",
      durationMs: Date.now() - start,
      forecourts: result,
      caches,
    });
  } catch (err) {
    return errorResponse(c, err);
  }
}

/** Runs only the prices sync. Requires forecourts to already exist. */
export async function adminCronPrices(c: AdminContext) {
  if (!isAuthorized(c)) return unauthorized(c);
  const start = Date.now();
  try {
    const tokenProvider = createAccessTokenProvider(
      c.env.KV,
      c.env.UPSTREAM_CLIENT_ID,
      c.env.UPSTREAM_CLIENT_SECRET,
      {
        disableRefresh: c.env.DISABLE_OAUTH_REFRESH === true,
        disableCache: c.env.DISABLE_OAUTH_CACHE === true,
      },
    );
    const result = await syncPrices(
      c.env.fuel_prices_db,
      c.env.KV,
      tokenProvider,
    );
    const caches = await refreshDerivedCaches(c.env.fuel_prices_db, c.env.KV);
    return c.json({
      status: "ok",
      durationMs: Date.now() - start,
      prices: result,
      caches,
    });
  } catch (err) {
    return errorResponse(c, err);
  }
}
