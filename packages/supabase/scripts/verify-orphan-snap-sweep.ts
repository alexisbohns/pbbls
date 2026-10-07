#!/usr/bin/env -S deno run --allow-env --allow-net
/**
 * Acceptance test for the orphan snap sweep (#322) — runs against the REMOTE
 * project.
 *
 * Seeds one throwaway user with the four cases from #322's plan, each a
 * `{user_id}/{snap_id}/` folder holding `original.jpg` + `thumb.jpg`:
 *
 *   published    a snap row exists (create_pebble)           → kept
 *   draft-only   named only in pebble_drafts.payload->snaps  → kept
 *   aged orphan  no reference, older than the grace period   → deleted
 *   fresh orphan no reference, younger than the grace period → kept
 *
 * then proves `list_orphan_snap_files` returns exactly the aged orphan, that
 * the `sweep-orphan-snaps` edge function's dry run deletes nothing, and that a
 * real run removes only the aged orphan through the Storage API. It also
 * proves the lister is not callable by a signed-in user and the function
 * refuses a user JWT, the anon key, and a sub-24h global sweep.
 *
 * "Aged" is relative: real orphans wait 24 hours, which no harness can. The
 * aged files are uploaded first, the run waits GRACE_SECONDS + 2, the fresh
 * one is uploaded, and both the lister and the function are called with
 * `min_age_seconds: GRACE_SECONDS` and `owner` = this run's user. The function
 * accepts a grace period under 24 hours only with an owner, so this run can
 * never touch another account's files.
 *
 * Run (needs the migration pushed and the function deployed first):
 *   SUPABASE_URL=... SUPABASE_ANON_KEY=... SUPABASE_SERVICE_ROLE_KEY=... \
 *     deno run --allow-env --allow-net packages/supabase/scripts/verify-orphan-snap-sweep.ts
 *
 * Needs the SERVICE ROLE, like verify-account-purge.ts: the lister is
 * service-role only, and so is the function. It is therefore a manual run and
 * deliberately NOT a step in .github/workflows/supabase.yml (see
 * packages/supabase/CLAUDE.md on why CI holds no service-role key).
 * Cleanup runs even on failure. Exits non-zero if any assertion fails.
 */

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL");
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY");
const SERVICE_ROLE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");

if (!SUPABASE_URL || !ANON_KEY || !SERVICE_ROLE) {
  console.error("SUPABASE_URL, SUPABASE_ANON_KEY and SUPABASE_SERVICE_ROLE_KEY must be set");
  Deno.exit(2);
}

const BUCKET = "pebbles-media";
const GRACE_SECONDS = 5;
const FILES = ["original.jpg", "thumb.jpg"];
const JPEG = new Uint8Array([0xff, 0xd8, 0xff, 0xd9]);

const admin = createClient(SUPABASE_URL, SERVICE_ROLE, { auth: { persistSession: false } });

let passed = 0;
let failed = 0;
function check(name: string, ok: boolean, detail?: string) {
  if (ok) {
    passed += 1;
    console.log(`✓ ${name}`);
  } else {
    failed += 1;
    console.log(`✗ ${name}${detail ? ` — ${detail}` : ""}`);
  }
}

function sameSet(a: string[], b: string[]): boolean {
  return a.length === b.length && [...a].sort().every((x, i) => x === [...b].sort()[i]);
}

async function listStorageFiles(prefix: string): Promise<string[]> {
  const files: string[] = [];
  const dirs = [prefix];
  while (dirs.length > 0) {
    const dir = dirs.pop() as string;
    const { data, error } = await admin.storage.from(BUCKET).list(dir, { limit: 100 });
    if (error) throw new Error(`storage list ${dir} failed: ${error.message}`);
    for (const entry of data ?? []) {
      if (entry.id === null) dirs.push(`${dir}/${entry.name}`);
      else files.push(`${dir}/${entry.name}`);
    }
  }
  return files;
}

async function invokeSweep(token: string, body: unknown): Promise<{ status: number; body: Record<string, unknown> }> {
  const res = await fetch(`${SUPABASE_URL}/functions/v1/sweep-orphan-snaps`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  return { status: res.status, body: await res.json().catch(() => ({})) };
}

const runId = crypto.randomUUID().slice(0, 8);
const email = `sweep-verify-${runId}@example.test`;
const password = `Sweep-${crypto.randomUUID()}`;
let userId: string | null = null;

try {
  const { data: created, error: createErr } = await admin.auth.admin.createUser({
    email, password, email_confirm: true,
  });
  if (createErr || !created?.user) throw new Error(`createUser: ${createErr?.message}`);
  userId = created.user.id;
  const owner = userId;

  // The files go up as the signed-in user, through the owner-scoped insert
  // policy, which is how every client uploads.
  const user = createClient(SUPABASE_URL, ANON_KEY, { auth: { persistSession: false } });
  const { data: session, error: signInErr } = await user.auth.signInWithPassword({ email, password });
  if (signInErr || !session?.session) throw new Error(`sign-in: ${signInErr?.message}`);
  const userToken = session.session.access_token;

  const snap = {
    published: crypto.randomUUID(),
    draft: crypto.randomUUID(),
    aged: crypto.randomUUID(),
    fresh: crypto.randomUUID(),
  };
  const folder = (id: string) => `${owner}/${id}`;
  const filesOf = (id: string) => FILES.map((f) => `${folder(id)}/${f}`);

  async function upload(id: string) {
    for (const path of filesOf(id)) {
      const { error } = await user.storage.from(BUCKET).upload(path, JPEG, { contentType: "image/jpeg" });
      if (error) throw new Error(`upload ${path}: ${error.message}`);
    }
  }

  await upload(snap.published);
  await upload(snap.draft);
  await upload(snap.aged);

  const { data: emotion } = await admin.from("emotions").select("id").limit(1).single();
  if (!emotion) throw new Error("reference data missing (emotion)");
  const { data: pebbleId, error: pebErr } = await user.rpc("create_pebble", {
    payload: {
      name: `sweep-verify ${runId}`,
      happened_at: "2026-10-07T12:00:00Z",
      intensity: 2,
      positiveness: 1,
      emotion_id: emotion.id,
      snaps: [{ id: snap.published, storage_path: folder(snap.published), sort_order: 0 }],
    },
  });
  if (pebErr || !pebbleId) throw new Error(`create_pebble: ${pebErr?.message}`);

  // The draft carries the snap the way every client writes it: {id,
  // storage_path, sort_order} and no public.snaps row until publish.
  const { error: draftErr } = await user.from("pebble_drafts").insert({
    user_id: owner,
    payload: {
      name: `sweep-verify draft ${runId}`,
      snaps: [{ id: snap.draft, storage_path: folder(snap.draft), sort_order: 0 }],
    },
  });
  if (draftErr) throw new Error(`insert draft: ${draftErr.message}`);

  await new Promise((r) => setTimeout(r, (GRACE_SECONDS + 2) * 1000));
  await upload(snap.fresh);

  // ---------------------------------------------------------------------------
  // 1. The lister.
  // ---------------------------------------------------------------------------
  const { data: listed, error: listErr } = await admin.rpc("list_orphan_snap_files", {
    p_min_age_seconds: GRACE_SECONDS,
    p_owner: owner,
  });
  const listedNames = ((listed ?? []) as { name: string }[]).map((r) => r.name);
  check("lister returns exactly the aged orphan",
    !listErr && sameSet(listedNames, filesOf(snap.aged)),
    listErr ? listErr.message : JSON.stringify(listedNames));

  const { data: listedAll, error: listAllErr } = await admin.rpc("list_orphan_snap_files", {
    p_min_age_seconds: 0,
    p_owner: owner,
  });
  const listedAllNames = ((listedAll ?? []) as { name: string }[]).map((r) => r.name);
  check("with no grace period it adds the fresh orphan, still not the published or draft snap",
    !listAllErr && sameSet(listedAllNames, [...filesOf(snap.aged), ...filesOf(snap.fresh)]),
    listAllErr ? listAllErr.message : JSON.stringify(listedAllNames));

  const { error: userListErr } = await user.rpc("list_orphan_snap_files", {
    p_min_age_seconds: 0,
    p_owner: owner,
  });
  check("a signed-in user cannot call the lister", userListErr !== null, "the call succeeded");

  // ---------------------------------------------------------------------------
  // 2. The edge function's guard.
  // ---------------------------------------------------------------------------
  const scoped = { owner, min_age_seconds: GRACE_SECONDS };
  const asUser = await invokeSweep(userToken, { ...scoped, dry_run: false });
  check("the function refuses a user JWT", asUser.status === 401, `status=${asUser.status}`);
  const asAnon = await invokeSweep(ANON_KEY, { ...scoped, dry_run: false });
  check("the function refuses the anon key", asAnon.status === 401, `status=${asAnon.status}`);
  const unscoped = await invokeSweep(SERVICE_ROLE, { min_age_seconds: GRACE_SECONDS, dry_run: true });
  check("a sub-24h sweep without an owner is refused", unscoped.status === 400, `status=${unscoped.status}`);

  // ---------------------------------------------------------------------------
  // 3. Dry run, then the real thing.
  // ---------------------------------------------------------------------------
  const dry = await invokeSweep(SERVICE_ROLE, scoped);
  check("an omitted dry_run is a dry run that finds the aged orphan",
    dry.status === 200 && dry.body.dry_run === true && dry.body.candidate_count === 2 &&
      dry.body.deleted_count === 0,
    `status=${dry.status} body=${JSON.stringify(dry.body)}`);
  const afterDry = await listStorageFiles(owner);
  check("the dry run deleted nothing", afterDry.length === 8, `found ${afterDry.length} files`);

  const live = await invokeSweep(SERVICE_ROLE, { ...scoped, dry_run: false });
  check("the live run deletes the two aged-orphan files and reports their bytes",
    live.status === 200 && live.body.deleted_count === 2 && live.body.bytes_freed === 2 * JPEG.length,
    `status=${live.status} body=${JSON.stringify(live.body)}`);

  const remaining = await listStorageFiles(owner);
  check("only the aged orphan is gone (published, draft-only and fresh survive)",
    sameSet(remaining, [...filesOf(snap.published), ...filesOf(snap.draft), ...filesOf(snap.fresh)]),
    JSON.stringify(remaining));

  const again = await invokeSweep(SERVICE_ROLE, { ...scoped, dry_run: false });
  check("a second run converges (nothing left to delete)",
    again.status === 200 && again.body.deleted_count === 0,
    `status=${again.status} body=${JSON.stringify(again.body)}`);
} catch (err) {
  failed += 1;
  console.error(`✗ aborted: ${err instanceof Error ? err.message : String(err)}`);
} finally {
  if (userId) {
    try {
      await admin.rpc("purge_account", { p_user_id: userId });
      const leftover = await listStorageFiles(userId).catch(() => [] as string[]);
      if (leftover.length > 0) await admin.storage.from(BUCKET).remove(leftover);
      await admin.auth.admin.deleteUser(userId);
    } catch (err) {
      console.error(`cleanup failed — remove ${email} manually: ${err}`);
    }
  }
}

console.log(`\nSummary: passed=${passed} failed=${failed}`);
Deno.exit(failed > 0 ? 1 : 0);
