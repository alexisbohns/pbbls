/**
 * Edge function: compose-pebble-update
 *
 * Client-facing counterpart to compose-pebble for the edit flow. Wraps the
 * deprecated update_pebble compat RPC:
 * 1. Auth-forwards the caller's JWT so update_pebble runs as the end user
 *    (ownership check inside the RPC)
 * 2. Calls update_pebble(p_pebble_id, payload)
 * 3. Responds with { pebble_id, karma_delta, render_svg: null, render_version: null }
 *
 * The render keys stay in the response, as null, so unmigrated clients still
 * decode it; the database no longer stores a rendered pebble.
 *
 * On RPC failure: 400 with the RPC error. There is no soft-success path any
 * more: no second step can fail after the update.
 */

import { serve } from "https://deno.land/std@0.224.0/http/server.ts";

import { createAuthForwardedClient } from "../_shared/supabase-client.ts";
import { readKarmaDelta } from "../_shared/karma-delta.ts";

interface RequestBody {
  pebble_id: string;
  // deno-lint-ignore no-explicit-any
  payload: any;
}

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, content-type, apikey, x-client-info",
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
    console.error("compose-pebble-update: body parse failed:", err);
    return json({ error: "invalid body: not JSON" }, 400);
  }
  if (!body || typeof body !== "object" || !body.pebble_id || !("payload" in body)) {
    console.error("compose-pebble-update: invalid body");
    return json({ error: "invalid body: pebble_id and payload required" }, 400);
  }

  // Call update_pebble with auth-forwarded client
  const authClient = createAuthForwardedClient(req);
  const { error: rpcError } = await authClient.rpc("update_pebble", {
    p_pebble_id: body.pebble_id,
    payload: body.payload,
  });

  if (rpcError) {
    console.error("compose-pebble-update: update_pebble rpc failed:", rpcError);
    return json({ error: rpcError.message }, 400);
  }

  const karmaDelta = await readKarmaDelta(authClient, body.pebble_id, "pebble_enriched");
  return json(
    { pebble_id: body.pebble_id, karma_delta: karmaDelta, render_svg: null, render_version: null },
    200,
  );
});

// deno-lint-ignore no-explicit-any
function json(body: any, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...CORS },
  });
}
