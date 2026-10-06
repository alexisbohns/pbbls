/**
 * The sweep-orphan-snaps handler, kept apart from `index.ts` so the tests can
 * drive it with a mocked client and fixed secrets (no network, no env).
 *
 * Which objects are orphans is decided in SQL by
 * `public.list_orphan_snap_files` (20261007131500): no `public.snaps` row, no
 * draft reference, older than the grace period. This file only authenticates
 * the caller, asks that function, and removes what it returns through the
 * Storage API. A SQL `delete from storage.objects` is refused by Supabase's
 * storage schema, which is why the old `sweep_orphan_snap_files()` never
 * deleted anything (#322).
 */

export const BUCKET = "pebbles-media";
/** Storage `remove()` accepts many paths per call; 100 matches delete-account. */
export const REMOVE_BATCH = 100;
/** The lister's default, and the floor for a sweep across every account. */
export const DEFAULT_MIN_AGE_SECONDS = 86_400;
/**
 * PostgREST caps an RPC's result set at `max_rows` (1000), so one listing can
 * be a partial view. A live sweep re-lists after deleting until a listing comes
 * back empty; this bounds that loop (at 1000 rows per pass, 20 passes is 20,000
 * objects, two orders of magnitude above today's backlog of 48).
 */
export const MAX_PASSES = 20;

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export interface OrphanRow {
  name: string;
  size: number | null;
  created_at: string;
}

interface ClientError {
  message: string;
}

/** The two calls the sweep makes — a structural subset of SupabaseClient. */
export interface SweepClient {
  rpc(
    fn: "list_orphan_snap_files",
    args: { p_min_age_seconds: number; p_owner: string | null },
  ): PromiseLike<{ data: OrphanRow[] | null; error: ClientError | null }>;
  storage: {
    from(bucket: string): {
      remove(paths: string[]): PromiseLike<{ data: { name: string }[] | null; error: ClientError | null }>;
    };
  };
}

export interface SweepOptions {
  dryRun: boolean;
  minAgeSeconds: number;
  owner: string | null;
}

export interface SweepResult {
  dry_run: boolean;
  min_age_seconds: number;
  owner: string | null;
  /** Orphans found by the first listing (what a dry run would delete). */
  candidate_count: number;
  candidate_bytes: number;
  deleted_count: number;
  bytes_freed: number;
}

export interface SweepDeps {
  /** SUPABASE_SERVICE_ROLE_KEY. */
  serviceRoleKey: string;
  /** ORPHAN_SWEEP_TOKEN: a narrower credential for the scheduled caller. */
  sweepToken: string;
  createClient(): SweepClient;
}

export class SweepError extends Error {
  constructor(message: string, readonly partial: SweepResult) {
    super(message);
  }
}

/**
 * Lists the orphans, then (unless dry-running) removes them in batches of
 * REMOVE_BATCH. Counts come from what Storage reports as removed, so an object
 * a client deleted in the meantime is not counted twice. Stops at the first
 * failed call and throws a SweepError carrying the counts so far: the sweep is
 * idempotent, so the next run picks up the rest.
 */
export async function runSweep(client: SweepClient, opts: SweepOptions): Promise<SweepResult> {
  const result: SweepResult = {
    dry_run: opts.dryRun,
    min_age_seconds: opts.minAgeSeconds,
    owner: opts.owner,
    candidate_count: 0,
    candidate_bytes: 0,
    deleted_count: 0,
    bytes_freed: 0,
  };

  let stalled = false;
  for (let pass = 0; pass < MAX_PASSES; pass++) {
    const { data, error } = await client.rpc("list_orphan_snap_files", {
      p_min_age_seconds: opts.minAgeSeconds,
      p_owner: opts.owner,
    });
    if (error) throw new SweepError(`list_orphan_snap_files failed: ${error.message}`, result);
    const rows = data ?? [];
    if (pass === 0) {
      result.candidate_count = rows.length;
      result.candidate_bytes = rows.reduce((sum, r) => sum + (r.size ?? 0), 0);
    }
    if (opts.dryRun || rows.length === 0) return result;

    const sizeByName = new Map(rows.map((r) => [r.name, r.size ?? 0]));
    let removedThisPass = 0;
    for (let i = 0; i < rows.length; i += REMOVE_BATCH) {
      const batch = rows.slice(i, i + REMOVE_BATCH).map((r) => r.name);
      const { data: removed, error: removeError } = await client.storage.from(BUCKET).remove(batch);
      if (removeError) throw new SweepError(`remove failed: ${removeError.message}`, result);
      for (const obj of removed ?? []) {
        removedThisPass += 1;
        result.deleted_count += 1;
        result.bytes_freed += sizeByName.get(obj.name) ?? 0;
      }
    }
    // One empty pass can be a race (a client removed the files between the
    // listing and the remove). Two in a row means Storage will not remove what
    // the lister keeps returning, which would otherwise loop MAX_PASSES times.
    if (removedThisPass === 0 && stalled) {
      throw new SweepError(`listed ${rows.length} orphans but storage removed none`, result);
    }
    stalled = removedThisPass === 0;
  }
  throw new SweepError(`orphans still listed after ${MAX_PASSES} passes`, result);
}

/** Constant-time compare; an empty expected secret never matches. */
export function secretMatches(presented: string, expected: string): boolean {
  if (!expected || presented.length !== expected.length) return false;
  let diff = 0;
  for (let i = 0; i < presented.length; i++) {
    diff |= presented.charCodeAt(i) ^ expected.charCodeAt(i);
  }
  return diff === 0;
}

type Parsed = { ok: true; opts: SweepOptions } | { ok: false; error: string };

/**
 * Body: `{ dry_run?: boolean, min_age_seconds?: integer, owner?: uuid }`.
 * `dry_run` defaults to TRUE: a caller has to ask for deletion in so many
 * words. A grace period under 24 hours is refused unless `owner` scopes the
 * sweep to one account, so no call can sweep everyone's in-flight uploads.
 */
export function parseBody(body: unknown): Parsed {
  if (body === null || typeof body !== "object" || Array.isArray(body)) {
    return { ok: false, error: "body must be a JSON object" };
  }
  const b = body as Record<string, unknown>;

  if (b.dry_run !== undefined && typeof b.dry_run !== "boolean") {
    return { ok: false, error: "dry_run must be a boolean" };
  }
  const dryRun = b.dry_run !== false;

  let owner: string | null = null;
  if (b.owner !== undefined && b.owner !== null) {
    if (typeof b.owner !== "string" || !UUID_RE.test(b.owner)) {
      return { ok: false, error: "owner must be a uuid" };
    }
    owner = b.owner.toLowerCase();
  }

  let minAgeSeconds = DEFAULT_MIN_AGE_SECONDS;
  if (b.min_age_seconds !== undefined) {
    if (typeof b.min_age_seconds !== "number" || !Number.isInteger(b.min_age_seconds) || b.min_age_seconds < 0) {
      return { ok: false, error: "min_age_seconds must be a non-negative integer" };
    }
    minAgeSeconds = b.min_age_seconds;
  }
  if (minAgeSeconds < DEFAULT_MIN_AGE_SECONDS && owner === null) {
    return { ok: false, error: `min_age_seconds below ${DEFAULT_MIN_AGE_SECONDS} needs an owner` };
  }

  return { ok: true, opts: { dryRun, minAgeSeconds, owner } };
}

export async function handleSweepRequest(req: Request, deps: SweepDeps): Promise<Response> {
  if (req.method !== "POST") {
    return json({ error: "method not allowed" }, 405);
  }

  // Without the service role key the function cannot list or delete anything,
  // and `secretMatches` would refuse every caller anyway; say so plainly.
  if (!deps.serviceRoleKey) {
    console.error("sweep-orphan-snaps: SUPABASE_SERVICE_ROLE_KEY not set");
    return json({ error: "service unavailable" }, 503);
  }

  const auth = req.headers.get("Authorization") ?? "";
  const presented = auth.startsWith("Bearer ") ? auth.slice(7) : "";
  // Both compares always run, so the response time does not say which secret
  // a near-miss was close to.
  const asServiceRole = secretMatches(presented, deps.serviceRoleKey);
  const asSweepToken = secretMatches(presented, deps.sweepToken);
  if (!asServiceRole && !asSweepToken) {
    console.error("sweep-orphan-snaps: auth failed");
    return json({ error: "unauthorized" }, 401);
  }

  let body: unknown = {};
  const raw = await req.text();
  if (raw.trim() !== "") {
    try {
      body = JSON.parse(raw);
    } catch (err) {
      console.error("sweep-orphan-snaps: body parse failed:", err);
      return json({ error: "invalid body" }, 400);
    }
  }
  const parsed = parseBody(body);
  if (!parsed.ok) {
    return json({ error: parsed.error }, 400);
  }

  try {
    const result = await runSweep(deps.createClient(), parsed.opts);
    console.log("sweep-orphan-snaps:", JSON.stringify(result));
    return json(result, 200);
  } catch (err) {
    console.error("sweep-orphan-snaps: sweep failed:", err);
    const message = err instanceof Error ? err.message : String(err);
    const partial = err instanceof SweepError ? err.partial : undefined;
    return json({ error: message, ...partial }, 500);
  }
}

function json(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
