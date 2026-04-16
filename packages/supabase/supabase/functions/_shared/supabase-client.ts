/**
 * Supabase client factories used by the compose-pebble and
 * backfill-pebble-render edge functions.
 */

import { createClient, type SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SUPABASE_ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

/**
 * Builds a supabase-js client that forwards the caller's JWT.
 * Use this when calling RPCs that depend on `auth.uid()`.
 *
 * If no Authorization header is present on the request, the client
 * operates as the anonymous user (anon key only). Callers are expected
 * to reject unauthenticated requests before invoking this factory.
 */
export function createAuthForwardedClient(req: Request): SupabaseClient {
  const auth = req.headers.get("Authorization") ?? "";
  return createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
    global: { headers: { Authorization: auth } },
    auth: { persistSession: false },
  });
}

/**
 * Builds a service-role supabase-js client that bypasses RLS.
 * Use this for server-owned writes (render columns) and backfill.
 */
export function createAdminClient(): SupabaseClient {
  return createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, {
    auth: { persistSession: false },
  });
}
