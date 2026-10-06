/**
 * Edge function: sweep-orphan-snaps
 *
 * Ops-only (#322). Deletes `pebbles-media` objects that nothing references any
 * more — no `public.snaps` row, no server draft naming them, older than 24
 * hours — and reports `deleted_count` / `bytes_freed`. The orphans come from
 * deleted pebbles (the `snaps` cascade cannot reach Storage) and from composes
 * abandoned after upload (client cleanup is best-effort on every surface).
 *
 * Callers: `.github/workflows/orphan-snap-sweep.yml` nightly, with
 * ORPHAN_SWEEP_TOKEN; a maintainer by hand, with either that token or the
 * service role key; and `scripts/verify-orphan-snap-sweep.ts`. Anything else —
 * a user JWT, the anon key, no header — is a 401. `verify_jwt` is off for this
 * function in config.toml because the token is not a JWT, so the bearer check
 * in sweep.ts is the only gate, and it fails closed when a secret is unset.
 *
 * Body (all optional): `{ dry_run: boolean = true, min_age_seconds: integer =
 * 86400, owner: uuid }`. Deleting needs `dry_run: false` spelled out. A grace
 * period under 24 hours is accepted only together with `owner`, which scopes
 * the sweep to one `{user_id}/` prefix (the harness uses this).
 *
 * The orphan definition lives in SQL, in `public.list_orphan_snap_files`.
 */

import { serve } from "https://deno.land/std@0.224.0/http/server.ts";

import { createAdminClient } from "../_shared/supabase-client.ts";
import { handleSweepRequest, type SweepClient } from "./sweep.ts";

const SERVICE_ROLE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const SWEEP_TOKEN = Deno.env.get("ORPHAN_SWEEP_TOKEN") ?? "";

function createSweepClient(): SweepClient {
  const admin = createAdminClient();
  return {
    rpc: (fn, args) => admin.rpc(fn, args),
    storage: {
      from: (bucket) => ({
        remove: (paths) => admin.storage.from(bucket).remove(paths),
      }),
    },
  };
}

serve((req: Request) =>
  handleSweepRequest(req, {
    serviceRoleKey: SERVICE_ROLE,
    sweepToken: SWEEP_TOKEN,
    createClient: createSweepClient,
  })
);
