/**
 * Edge function: compose-pebble
 *
 * Client-facing. Wraps the existing create_pebble RPC:
 * 1. Auth-forwards the caller's JWT so the RPC runs as the end user
 * 2. Calls create_pebble(payload) → returns pebble_id
 * 3. Calls compose-and-write → writes render columns + returns composed output
 * 4. Responds with { pebble_id, render_svg, render_manifest, render_version }
 *
 * On RPC failure: 4xx with the RPC error.
 * On compose failure after successful insert: 500 with pebble_id in the body
 * so the iOS client can advance to the detail sheet (soft-success path).
 */

import { serve } from "https://deno.land/std@0.224.0/http/server.ts";

import { createAuthForwardedClient, createAdminClient } from "../_shared/supabase-client.ts";
import { composeAndWriteRender } from "../_shared/compose-and-write.ts";

interface RequestBody {
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

  // Step 1: call create_pebble RPC with auth-forwarded client
  const authClient = createAuthForwardedClient(req);
  const { data: pebbleId, error: rpcError } = await authClient.rpc("create_pebble", {
    payload: body.payload,
  });

  if (rpcError || !pebbleId) {
    console.error("compose-pebble: create_pebble rpc failed:", rpcError);
    return json({ error: rpcError?.message ?? "create_pebble returned no id" }, 400);
  }

  // Step 2: compose + write-back
  const admin = createAdminClient();
  try {
    const rendered = await composeAndWriteRender(admin, pebbleId as string);
    return json({ pebble_id: pebbleId, ...rendered }, 200);
  } catch (err) {
    console.error("compose-pebble: composeAndWrite failed:", err);
    // Soft-success: pebble exists, render failed. Return 500 with pebble_id
    // so iOS can advance to the detail sheet with text-only fallback.
    return json(
      {
        error: `compose failed: ${err instanceof Error ? err.message : String(err)}`,
        pebble_id: pebbleId,
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
