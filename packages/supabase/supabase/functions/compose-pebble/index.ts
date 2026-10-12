/**
 * Edge function: compose-pebble
 *
 * Client-facing. Wraps the deprecated create_pebble compat RPC:
 * 1. Auth-forwards the caller's JWT so the RPC runs as the end user
 * 2. Calls create_pebble(payload) -> returns pebble_id
 * 3. Responds with { pebble_id, karma_delta, render_svg: null, render_version: null }
 *
 * The render keys stay in the response, as null, so unmigrated clients still
 * decode it; the database no longer stores a rendered pebble.
 *
 * On RPC failure: 400 with the RPC error. There is no soft-success path any
 * more: no second step can fail after the insert.
 */

import { serve } from "https://deno.land/std@0.224.0/http/server.ts";

import { createAuthForwardedClient } from "../_shared/supabase-client.ts";
import { readKarmaDelta } from "../_shared/karma-delta.ts";

interface RequestBody {
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

  // Parse body
  let body: RequestBody;
  try {
    body = await req.json();
  } catch (err) {
    console.error("compose-pebble: body parse failed:", err);
    return json({ error: "invalid body: not JSON" }, 400);
  }
  if (!body || typeof body !== "object" || !("payload" in body)) {
    console.error("compose-pebble: invalid body — missing payload");
    return json({ error: "invalid body: missing payload" }, 400);
  }

  // Call create_pebble RPC with auth-forwarded client
  const authClient = createAuthForwardedClient(req);
  const { data: pebbleId, error: rpcError } = await authClient.rpc("create_pebble", {
    payload: body.payload,
  });

  if (rpcError || !pebbleId) {
    console.error("compose-pebble: create_pebble rpc failed:", rpcError);
    return json({ error: rpcError?.message ?? "create_pebble returned no id" }, 400);
  }

  const karmaDelta = await readKarmaDelta(authClient, pebbleId as string, "pebble_created");
  return json(
    { pebble_id: pebbleId, karma_delta: karmaDelta, render_svg: null, render_version: null },
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
