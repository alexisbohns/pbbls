/**
 * Edge function: compose-pebble-update
 *
 * Client-facing counterpart to compose-pebble for the edit flow. Wraps
 * update_pebble RPC + compose-and-write so that editing a pebble (for
 * example, associating a personal glyph) re-renders render_svg atomically
 * from the iOS client's perspective.
 *
 * 1. Auth-forwards the caller's JWT so update_pebble runs as the end user
 *    (ownership check inside the RPC)
 * 2. Calls update_pebble(p_pebble_id, payload)
 * 3. Calls compose-and-write → writes render columns + returns composed output
 * 4. Responds with { pebble_id, render_svg, render_manifest, render_version }
 *
 * On RPC failure: 4xx with the RPC error.
 * On compose failure after successful update: 500 with pebble_id in the body
 * so the iOS client can advance to the detail sheet (soft-success path —
 * mirrors compose-pebble).
 */

import { serve } from "https://deno.land/std@0.224.0/http/server.ts";

import { createAuthForwardedClient, createAdminClient } from "../_shared/supabase-client.ts";
import { composeAndWriteRender } from "../_shared/compose-and-write.ts";

interface RequestBody {
  pebble_id: string;
  // deno-lint-ignore no-explicit-any
  payload: any;
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
    console.error("compose-pebble-update: body parse failed:", err);
    return json({ error: "invalid body: not JSON" }, 400);
  }
  if (!body || typeof body !== "object" || !body.pebble_id || !("payload" in body)) {
    console.error("compose-pebble-update: invalid body");
    return json({ error: "invalid body: pebble_id and payload required" }, 400);
  }

  // Step 1: update_pebble with auth-forwarded client
  const authClient = createAuthForwardedClient(req);
  const { error: rpcError } = await authClient.rpc("update_pebble", {
    p_pebble_id: body.pebble_id,
    payload: body.payload,
  });

  if (rpcError) {
    console.error("compose-pebble-update: update_pebble rpc failed:", rpcError);
    return json({ error: rpcError.message }, 400);
  }

  // Step 2: compose + write-back
  const admin = createAdminClient();
  try {
    const rendered = await composeAndWriteRender(admin, body.pebble_id);
    return json({ pebble_id: body.pebble_id, ...rendered }, 200);
  } catch (err) {
    console.error("compose-pebble-update: composeAndWrite failed:", err);
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
