/**
 * Edge function: recompose-pebble
 *
 * Client-facing. Takes { pebble_id } and re-runs the compose engine against
 * an existing pebble owned by the caller. Intended for use after an
 * update_pebble RPC on the web client, to keep the saved render_svg fresh
 * when the user edits intensity / valence / emotion / glyph.
 *
 * Auth model mirrors compose-pebble:
 *   1. Auth-forwards the caller's JWT to verify they own the pebble (via RLS
 *      on the pebbles table — a SELECT returning 0 rows means "not yours").
 *   2. Uses an admin client only to run compose-and-write (which bypasses
 *      RLS for the single UPDATE to the render columns).
 *
 * This is the auth-forwarded counterpart of backfill-pebble-render (which
 * requires the service-role bearer token and is ops-only).
 *
 * Response: { pebble_id, render_svg, render_manifest, render_version }.
 * On compose failure: 500 with pebble_id so clients can fall back to the
 * local engine render for display.
 */

import { serve } from "https://deno.land/std@0.224.0/http/server.ts";

import { createAuthForwardedClient, createAdminClient } from "../_shared/supabase-client.ts";
import { composeAndWriteRender } from "../_shared/compose-and-write.ts";

interface RequestBody {
  pebble_id: string;
}

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: CORS });
  }
  if (req.method !== "POST") {
    return json({ error: "method not allowed" }, 405);
  }

  let body: RequestBody;
  try {
    body = await req.json();
  } catch (err) {
    console.error("recompose-pebble: body parse failed:", err);
    return json({ error: "invalid body: not JSON" }, 400);
  }
  if (!body || typeof body.pebble_id !== "string") {
    console.error("recompose-pebble: invalid pebble_id");
    return json({ error: "invalid pebble_id" }, 400);
  }

  // Ownership check via RLS: selecting the pebble with the caller's JWT
  // returns 0 rows if they don't own it (or if it doesn't exist).
  const authClient = createAuthForwardedClient(req);
  const { data: ownedPebble, error: ownError } = await authClient
    .from("pebbles")
    .select("id")
    .eq("id", body.pebble_id)
    .maybeSingle();

  if (ownError) {
    console.error("recompose-pebble: ownership check failed:", ownError);
    return json({ error: ownError.message }, 400);
  }
  if (!ownedPebble) {
    return json({ error: "pebble not found" }, 404);
  }

  const admin = createAdminClient();
  try {
    const rendered = await composeAndWriteRender(admin, body.pebble_id);
    return json({ pebble_id: body.pebble_id, ...rendered }, 200);
  } catch (err) {
    console.error("recompose-pebble: compose failed:", err);
    // Soft-success: pebble exists, compose failed. Return 500 with pebble_id
    // so the client can fall back to the local engine render for display.
    return json(
      {
        error: `compose failed: ${err instanceof Error ? err.message : String(err)}`,
        pebble_id: body.pebble_id,
      },
      500,
    );
  }
});

// deno-lint-ignore no-explicit-any
function json(body: any, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...CORS },
  });
}
