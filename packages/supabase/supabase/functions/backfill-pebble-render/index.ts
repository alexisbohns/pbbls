/**
 * Edge function: backfill-pebble-render
 *
 * Ops-only. Takes { pebble_id }, requires the caller to present the
 * SUPABASE_SERVICE_ROLE_KEY as a bearer token, and calls
 * compose-and-write against an existing pebble.
 *
 * Used by scripts/backfill-renders.ts to rehydrate pebbles whose render
 * columns are NULL (legacy pebbles, failed composes, post-engine-bump
 * re-renders).
 */

import { serve } from "https://deno.land/std@0.224.0/http/server.ts";

import { createAdminClient } from "../_shared/supabase-client.ts";
import { composeAndWriteRender } from "../_shared/compose-and-write.ts";

interface RequestBody {
  pebble_id: string;
}

const SERVICE_ROLE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

serve(async (req: Request) => {
  if (req.method !== "POST") {
    return json({ error: "method not allowed" }, 405);
  }

  // Refuse to serve if SERVICE_ROLE is unset — constantTimeEqual("", "") would
  // otherwise return true and anyone presenting no bearer would be let through.
  if (!SERVICE_ROLE) {
    console.error("backfill-pebble-render: SUPABASE_SERVICE_ROLE_KEY not set");
    return json({ error: "service unavailable" }, 503);
  }

  // Bearer check: constant-time compare against the service role key.
  const auth = req.headers.get("Authorization") ?? "";
  const presented = auth.startsWith("Bearer ") ? auth.slice(7) : "";
  if (!constantTimeEqual(presented, SERVICE_ROLE)) {
    console.error("backfill-pebble-render: auth failed");
    return json({ error: "unauthorized" }, 401);
  }

  let body: RequestBody;
  try {
    body = await req.json();
  } catch (err) {
    console.error("backfill-pebble-render: body parse failed:", err);
    return json({ error: "invalid body" }, 400);
  }
  if (!body || typeof body.pebble_id !== "string") {
    console.error("backfill-pebble-render: invalid pebble_id");
    return json({ error: "invalid pebble_id" }, 400);
  }

  const admin = createAdminClient();
  try {
    const rendered = await composeAndWriteRender(admin, body.pebble_id);
    return json({ pebble_id: body.pebble_id, ...rendered }, 200);
  } catch (err) {
    console.error("backfill-pebble-render: compose failed:", err);
    const message = err instanceof Error ? err.message : String(err);
    const status = message.includes("not found") ? 404 : 500;
    return json({ error: message, pebble_id: body.pebble_id }, status);
  }
});

function constantTimeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) {
    diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
  return diff === 0;
}

// deno-lint-ignore no-explicit-any
function json(body: any, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
// redeploy 2026-04-30 — drop render_manifest writeback (post-#333)
