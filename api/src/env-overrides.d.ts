/**
 * Widen boolean vars that wrangler types as literal `false` (from their
 * default values). At runtime they can be overridden to `true` via
 * secrets or per-environment config.
 */
declare namespace Cloudflare {
  interface Env {
    DISABLE_OAUTH_REFRESH: boolean;
    DISABLE_OAUTH_CACHE: boolean;
  }
}
