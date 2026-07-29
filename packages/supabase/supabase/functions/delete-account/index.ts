/**
 * Edge function: delete-account
 *
 * Client-facing. Full erasure for the CALLING user (identity from the
 * forwarded JWT — never from the body, so nobody deletes anyone else):
 *   1. purge_account(p_user_id)          — single-transaction SQL purge
 *   2. empty storage prefix pebbles-media/{user_id}/
 *   3. auth.admin.deleteUser(user_id)
 *
 * Idempotent & resumable. The order is load-bearing: the storage prefix is
 * derived from the user id alone (no DB state needed after the purge), and
 * the auth row goes LAST because it is the caller's ability to retry.
 * Resume matrix:
 *   - fail in 1 → transaction rolled back, nothing changed, retry.
 *   - fail in 2 → DB already purged; re-run: purge matches zero rows,
 *     storage walk picks up where it left off.
 *   - fail in 3 → re-run: purge no-ops, walk finds an empty prefix,
 *     deleteUser is retried.
 *   - after 3   → the gateway still accepts the signed JWT but
 *     auth.getUser() finds no user → 401, which IS the converged state.
 * If a user's JWT expires mid-failure, the service role can finish the job
 * manually (purge_account + auth.admin.deleteUser).
 */

import { serve } from "https://deno.land/std@0.224.0/http/server.ts";

import { createAuthForwardedClient, createAdminClient } from "../_shared/supabase-client.ts";

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, content-type, apikey, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const BUCKET = "pebbles-media";
const LIST_PAGE = 100;
const REMOVE_BATCH = 100;
// Removing while paginating can skip entries, so we re-walk until a pass
// finds nothing. The cap only guards against a pathological loop.
const MAX_SWEEPS = 5;

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: CORS });
  }
  if (req.method !== "POST") {
    return json({ error: "method not allowed" }, 405);
  }

  const authClient = createAuthForwardedClient(req);
  const { data: userData, error: userError } = await authClient.auth.getUser();
  const userId = userData?.user?.id;
  if (userError || !userId) {
    console.error("delete-account: auth.getUser failed:", userError);
    return json({ error: "not_authenticated" }, 401);
  }

  const admin = createAdminClient();

  // Step 1 — SQL purge (atomic; a re-run converges to zero counts).
  const { data: purged, error: purgeError } = await admin.rpc("purge_account", {
    p_user_id: userId,
  });
  if (purgeError) {
    console.error("delete-account: purge_account failed:", purgeError);
    return json({ error: `purge failed: ${purgeError.message}` }, 500);
  }

  // Step 2 — storage. list() is non-recursive and paginated, so BFS the
  // folder tree ({user_id}/{snap_id}/*.jpg today, deeper tomorrow) and
  // remove in batches, sweeping until an empty pass.
  try {
    for (let sweep = 0; ; sweep++) {
      const files = await listAllFiles(admin, userId);
      if (files.length === 0) break;
      if (sweep >= MAX_SWEEPS) {
        throw new Error(`prefix not empty after ${MAX_SWEEPS} sweeps`);
      }
      for (let i = 0; i < files.length; i += REMOVE_BATCH) {
        const { error } = await admin.storage
          .from(BUCKET)
          .remove(files.slice(i, i + REMOVE_BATCH));
        if (error) throw new Error(`remove failed: ${error.message}`);
      }
    }
  } catch (err) {
    console.error("delete-account: storage purge failed:", err);
    return json(
      { error: `storage purge failed: ${err instanceof Error ? err.message : String(err)}` },
      500,
    );
  }

  // Step 3 — the auth row, last.
  const { error: deleteError } = await admin.auth.admin.deleteUser(userId);
  if (deleteError) {
    console.error("delete-account: deleteUser failed:", deleteError);
    return json({ error: `auth delete failed: ${deleteError.message}` }, 500);
  }

  return json({ ok: true, purged }, 200);
});

// deno-lint-ignore no-explicit-any
async function listAllFiles(admin: any, userId: string): Promise<string[]> {
  const files: string[] = [];
  const dirs = [userId];
  while (dirs.length > 0) {
    const dir = dirs.pop() as string;
    let offset = 0;
    for (;;) {
      const { data, error } = await admin.storage
        .from(BUCKET)
        .list(dir, { limit: LIST_PAGE, offset });
      if (error) throw new Error(`list ${dir} failed: ${error.message}`);
      for (const entry of data ?? []) {
        // Storage returns folders as entries with a null id.
        if (entry.id === null) dirs.push(`${dir}/${entry.name}`);
        else files.push(`${dir}/${entry.name}`);
      }
      if (!data || data.length < LIST_PAGE) break;
      offset += data.length;
    }
  }
  return files;
}

// deno-lint-ignore no-explicit-any
function json(body: any, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...CORS },
  });
}
