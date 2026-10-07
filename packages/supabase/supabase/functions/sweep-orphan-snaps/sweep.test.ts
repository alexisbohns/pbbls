/**
 * sweep-orphan-snaps — auth guard, body parsing and batching, against a mocked
 * client. No network: the lister and Storage are fakes that record their calls.
 *
 * Run: deno test packages/supabase/supabase/functions/sweep-orphan-snaps/
 */

import { assert, assertEquals } from "https://deno.land/std@0.224.0/assert/mod.ts";

import {
  BUCKET,
  DEFAULT_MIN_AGE_SECONDS,
  handleSweepRequest,
  type OrphanRow,
  parseBody,
  REMOVE_BATCH,
  runSweep,
  type SweepClient,
  type SweepDeps,
} from "./sweep.ts";

const SERVICE_ROLE = "service-role-key-for-tests";
const SWEEP_TOKEN = "orphan-sweep-token-for-tests";
const OWNER = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";

function rows(n: number, size = 10): OrphanRow[] {
  return Array.from({ length: n }, (_, i) => ({
    name: `${OWNER}/${crypto.randomUUID()}/${i % 2 === 0 ? "original" : "thumb"}.jpg`,
    size,
    created_at: "2026-10-01T00:00:00Z",
  }));
}

interface FakeOptions {
  /** Successive listings; the last one repeats once exhausted. */
  listings: OrphanRow[][];
  listError?: string;
  removeError?: string;
  /** Paths Storage pretends are already gone (removed by someone else). */
  alreadyGone?: Set<string>;
}

function fakeClient(opts: FakeOptions) {
  const calls = {
    rpc: [] as { fn: string; args: unknown }[],
    remove: [] as { bucket: string; paths: string[] }[],
  };
  let listing = 0;
  const removed = new Set<string>();
  const client: SweepClient = {
    rpc(fn, args) {
      calls.rpc.push({ fn, args });
      if (opts.listError) return Promise.resolve({ data: null, error: { message: opts.listError } });
      const page = opts.listings[Math.min(listing, opts.listings.length - 1)] ?? [];
      listing += 1;
      return Promise.resolve({ data: page.filter((r) => !removed.has(r.name)), error: null });
    },
    storage: {
      from(bucket) {
        return {
          remove(paths) {
            calls.remove.push({ bucket, paths });
            if (opts.removeError) {
              return Promise.resolve({ data: null, error: { message: opts.removeError } });
            }
            const gone = paths.filter((p) => !opts.alreadyGone?.has(p));
            for (const p of gone) removed.add(p);
            return Promise.resolve({ data: gone.map((name) => ({ name })), error: null });
          },
        };
      },
    },
  };
  return { client, calls };
}

function deps(client: SweepClient, overrides: Partial<SweepDeps> = {}): SweepDeps {
  return { serviceRoleKey: SERVICE_ROLE, sweepToken: SWEEP_TOKEN, createClient: () => client, ...overrides };
}

function post(body: unknown, token?: string): Request {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (token !== undefined) headers.Authorization = `Bearer ${token}`;
  return new Request("http://localhost/sweep-orphan-snaps", {
    method: "POST",
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

// ── Auth guard ──────────────────────────────────────────────

Deno.test("refuses a request with no Authorization header", async () => {
  const { client, calls } = fakeClient({ listings: [rows(2)] });
  const res = await handleSweepRequest(post({}), deps(client));
  assertEquals(res.status, 401);
  assertEquals(calls.rpc.length, 0);
});

Deno.test("refuses a bearer that is neither secret (a user JWT, the anon key)", async () => {
  const { client, calls } = fakeClient({ listings: [rows(2)] });
  for (const token of ["eyJhbGciOiJIUzI1NiJ9.user.jwt", "anon-key", SERVICE_ROLE + "x", ""]) {
    const res = await handleSweepRequest(post({}, token), deps(client));
    assertEquals(res.status, 401, `token ${JSON.stringify(token)}`);
  }
  assertEquals(calls.rpc.length, 0);
});

Deno.test("an unset sweep token matches nothing, not an empty bearer", async () => {
  const { client, calls } = fakeClient({ listings: [rows(2)] });
  const res = await handleSweepRequest(post({}, ""), deps(client, { sweepToken: "" }));
  assertEquals(res.status, 401);
  const bare = new Request("http://localhost/", { method: "POST", headers: { Authorization: "Bearer " } });
  assertEquals((await handleSweepRequest(bare, deps(client, { sweepToken: "" }))).status, 401);
  assertEquals(calls.rpc.length, 0);
});

Deno.test("fails closed with 503 when the service role key is unset", async () => {
  const { client, calls } = fakeClient({ listings: [rows(2)] });
  const res = await handleSweepRequest(post({}, ""), deps(client, { serviceRoleKey: "" }));
  assertEquals(res.status, 503);
  assertEquals(calls.rpc.length, 0);
});

Deno.test("accepts the service role key and the sweep token", async () => {
  for (const token of [SERVICE_ROLE, SWEEP_TOKEN]) {
    const { client } = fakeClient({ listings: [rows(2)] });
    const res = await handleSweepRequest(post({}, token), deps(client));
    assertEquals(res.status, 200, token);
  }
});

Deno.test("only POST is served", async () => {
  const { client } = fakeClient({ listings: [[]] });
  const req = new Request("http://localhost/", { method: "GET", headers: { Authorization: `Bearer ${SERVICE_ROLE}` } });
  assertEquals((await handleSweepRequest(req, deps(client))).status, 405);
});

// ── Body ────────────────────────────────────────────────────

Deno.test("an empty body is a dry run at the 24-hour default", async () => {
  const { client, calls } = fakeClient({ listings: [rows(3)] });
  const res = await handleSweepRequest(post(undefined, SWEEP_TOKEN), deps(client));
  assertEquals(res.status, 200);
  const body = await res.json();
  assertEquals(body.dry_run, true);
  assertEquals(body.candidate_count, 3);
  assertEquals(body.deleted_count, 0);
  assertEquals(calls.remove.length, 0);
  assertEquals(calls.rpc[0].args, { p_min_age_seconds: DEFAULT_MIN_AGE_SECONDS, p_owner: null });
});

Deno.test("parseBody rejects malformed fields", () => {
  for (const bad of [
    [],
    "x",
    { dry_run: "false" },
    { min_age_seconds: -1 },
    { min_age_seconds: 1.5 },
    { min_age_seconds: "86400" },
    { owner: "not-a-uuid" },
    { owner: 42 },
  ]) {
    assertEquals(parseBody(bad).ok, false, JSON.stringify(bad));
  }
});

Deno.test("a grace period under 24 hours needs an owner", () => {
  assertEquals(parseBody({ min_age_seconds: 60 }).ok, false);
  const scoped = parseBody({ min_age_seconds: 60, owner: OWNER.toUpperCase(), dry_run: false });
  assert(scoped.ok);
  assertEquals(scoped.opts, { dryRun: false, minAgeSeconds: 60, owner: OWNER });
  assert(parseBody({ min_age_seconds: 172_800 }).ok);
});

Deno.test("invalid JSON is a 400, after auth", async () => {
  const { client } = fakeClient({ listings: [[]] });
  const req = new Request("http://localhost/", {
    method: "POST",
    headers: { Authorization: `Bearer ${SERVICE_ROLE}` },
    body: "{nope",
  });
  assertEquals((await handleSweepRequest(req, deps(client))).status, 400);
  const unauth = new Request("http://localhost/", { method: "POST", body: "{nope" });
  assertEquals((await handleSweepRequest(unauth, deps(client))).status, 401);
});

// ── Batching ────────────────────────────────────────────────

Deno.test("removes in batches of REMOVE_BATCH and reports deleted_count / bytes_freed", async () => {
  const listed = rows(REMOVE_BATCH * 2 + 37, 7);
  const { client, calls } = fakeClient({ listings: [listed] });
  const result = await runSweep(client, { dryRun: false, minAgeSeconds: DEFAULT_MIN_AGE_SECONDS, owner: null });

  assertEquals(calls.remove.map((c) => c.paths.length), [REMOVE_BATCH, REMOVE_BATCH, 37]);
  assert(calls.remove.every((c) => c.bucket === BUCKET));
  assertEquals(calls.remove.flatMap((c) => c.paths), listed.map((r) => r.name));
  assertEquals(result.candidate_count, listed.length);
  assertEquals(result.deleted_count, listed.length);
  assertEquals(result.bytes_freed, listed.length * 7);
  // The re-listing after the deletes came back empty, which ended the sweep.
  assertEquals(calls.rpc.length, 2);
});

Deno.test("re-lists until empty, for a listing PostgREST truncated", async () => {
  const first = rows(5);
  const second = rows(3);
  const { client, calls } = fakeClient({ listings: [first, second, []] });
  const result = await runSweep(client, { dryRun: false, minAgeSeconds: DEFAULT_MIN_AGE_SECONDS, owner: null });
  assertEquals(result.candidate_count, 5);
  assertEquals(result.deleted_count, 8);
  assertEquals(calls.rpc.length, 3);
});

Deno.test("counts only what Storage reports removed", async () => {
  const listed = rows(4, 100);
  listed[0].size = null;
  const { client } = fakeClient({ listings: [listed, []], alreadyGone: new Set([listed[1].name]) });
  const result = await runSweep(client, { dryRun: false, minAgeSeconds: DEFAULT_MIN_AGE_SECONDS, owner: null });
  assertEquals(result.deleted_count, 3);
  assertEquals(result.bytes_freed, 200);
});

Deno.test("a dry run lists once and removes nothing", async () => {
  const { client, calls } = fakeClient({ listings: [rows(250, 4)] });
  const result = await runSweep(client, { dryRun: true, minAgeSeconds: DEFAULT_MIN_AGE_SECONDS, owner: OWNER });
  assertEquals(calls.remove.length, 0);
  assertEquals(calls.rpc.length, 1);
  assertEquals(calls.rpc[0].args, { p_min_age_seconds: DEFAULT_MIN_AGE_SECONDS, p_owner: OWNER });
  assertEquals(result.candidate_count, 250);
  assertEquals(result.candidate_bytes, 1000);
  assertEquals(result.deleted_count, 0);
});

Deno.test("a lister error is a 500 and nothing is removed", async () => {
  const { client, calls } = fakeClient({ listings: [[]], listError: "permission denied" });
  const res = await handleSweepRequest(post({ dry_run: false }, SERVICE_ROLE), deps(client));
  assertEquals(res.status, 500);
  assertEquals(calls.remove.length, 0);
});

Deno.test("a remove error stops the sweep and returns the counts so far", async () => {
  const { client, calls } = fakeClient({ listings: [rows(REMOVE_BATCH + 1)], removeError: "boom" });
  const res = await handleSweepRequest(post({ dry_run: false }, SWEEP_TOKEN), deps(client));
  assertEquals(res.status, 500);
  const body = await res.json();
  assertEquals(body.deleted_count, 0);
  assertEquals(body.candidate_count, REMOVE_BATCH + 1);
  assertEquals(calls.remove.length, 1);
});

Deno.test("stops when Storage keeps refusing to remove what the lister returns", async () => {
  const listed = rows(2);
  const { client, calls } = fakeClient({ listings: [listed], alreadyGone: new Set(listed.map((r) => r.name)) });
  let threw = false;
  try {
    await runSweep(client, { dryRun: false, minAgeSeconds: DEFAULT_MIN_AGE_SECONDS, owner: null });
  } catch {
    threw = true;
  }
  assert(threw);
  // One stalled pass is tolerated as a race; the second one stops the sweep.
  assertEquals(calls.rpc.length, 2);
});
