#!/usr/bin/env -S deno run --allow-env --allow-net
/**
 * Acceptance test for drafts (M47) — runs against the REMOTE project.
 *
 * The three clients are hand-written and share one contract: `pebble_drafts.payload`
 * is a partial `compose-pebble` payload (design D2). Nothing in a single app's
 * test suite can prove that contract holds end to end, so this exercises it
 * against the real database and the real edge function:
 *
 *   1. RLS — a second user cannot read, write, delete, or upsert-hijack a draft.
 *   2. Karma — the whole draft lifecycle emits zero `karma_events` (design D8).
 *   3. Upsert — save-without-id creates, save-with-id replaces in place.
 *   4. Payload fidelity — iOS-shaped and web-shaped payloads survive jsonb,
 *      including explicit nulls, empty arrays, and nested snap descriptors.
 *   5. Publish — a draft payload publishes through `compose-pebble` exactly once,
 *      `happened_at` verbatim (design D6), and the draft is then removable.
 *   6. Purge — `purge_account` reports the drafts it deleted.
 *
 * Run:
 *   SUPABASE_URL=... SUPABASE_ANON_KEY=... \
 *     deno run --allow-env --allow-net packages/supabase/scripts/verify-pebble-drafts.ts
 *
 * Deliberately needs NO service-role key: it signs up throwaway users and
 * deletes them through the real `delete-account` edge function, so it is
 * runnable by anyone who can run the app. Cleanup runs even on failure.
 * Exits non-zero if any assertion fails.
 *
 * STANDING RULE: when the draft payload gains a key, add it to `iosShaped` below
 * so the round-trip assertion keeps covering the whole contract.
 */

const SUPABASE_URL = Deno.env.get("SUPABASE_URL");
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY");

if (!SUPABASE_URL || !ANON_KEY) {
  console.error("SUPABASE_URL and SUPABASE_ANON_KEY must be set");
  Deno.exit(2);
}

import { createClient, type SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";

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

const runId = crypto.randomUUID().slice(0, 8);
const password = `Drafts-${crypto.randomUUID()}`;

type TestUser = { client: SupabaseClient; id: string; token: string };

async function signUp(label: string): Promise<TestUser> {
  const client = createClient(SUPABASE_URL!, ANON_KEY!, { auth: { persistSession: false } });
  const email = `drafts-verify-${label}-${runId}@example.test`;
  const { data, error } = await client.auth.signUp({ email, password });
  if (error || !data.session || !data.user) {
    throw new Error(`signUp ${label}: ${error?.message ?? "no session (email confirmations on?)"}`);
  }
  return { client, id: data.user.id, token: data.session.access_token };
}

/** The real client teardown path — no service role needed. */
async function deleteAccount(token: string): Promise<Response> {
  return await fetch(`${SUPABASE_URL}/functions/v1/delete-account`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      apikey: ANON_KEY!,
      "Content-Type": "application/json",
    },
  });
}

/**
 * Order-insensitive JSON, recursively. `jsonb` does not preserve key order at
 * any depth, so a raw `JSON.stringify` comparison reports a perfectly intact
 * payload as changed. Sorting keys keeps the assertion sensitive to values —
 * which is the thing that must not drift — and blind to storage ordering.
 */
function canonical(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(canonical).join(",")}]`;
  if (value !== null && typeof value === "object") {
    const entries = Object.entries(value as Record<string, unknown>).sort(([x], [y]) =>
      x < y ? -1 : x > y ? 1 : 0
    );
    return `{${entries.map(([k, v]) => `${JSON.stringify(k)}:${canonical(v)}`).join(",")}}`;
  }
  return JSON.stringify(value) ?? "null";
}

async function countRows(client: SupabaseClient, table: string): Promise<number> {
  const { count } = await client.from(table).select("*", { count: "exact", head: true });
  return count ?? 0;
}

let alice: TestUser | null = null;
let bob: TestUser | null = null;

try {
  alice = await signUp("a");
  bob = await signUp("b");
  console.log(`alice=${alice.id} bob=${bob.id}\n`);
  const a = alice.client;

  const { data: emotion } = await a.from("emotions").select("id").limit(1).single();
  const { data: domain } = await a.from("domains").select("id").limit(1).single();
  if (!emotion || !domain) throw new Error("reference data missing (emotion / domain)");

  // ---------------------------------------------------------------------------
  // 1. Quick capture: a name is enough, and it costs no karma.
  // ---------------------------------------------------------------------------
  const { data: quick, error: quickErr } = await a
    .from("pebble_drafts")
    .upsert({ user_id: alice.id, payload: { name: `quick ${runId}` } }, { onConflict: "id" })
    .select("id, payload, created_at, updated_at")
    .single();
  check("upsert without an id creates a draft", !quickErr && !!quick?.id, quickErr?.message);
  check("a name alone is a valid draft", quick?.payload?.name === `quick ${runId}`);
  check("quick capture emits no karma", (await countRows(a, "karma_events")) === 0);
  const draftId = quick!.id as string;

  // ---------------------------------------------------------------------------
  // 2. Second save of the same draft replaces the payload wholesale — the reason
  //    this is a jsonb column and not coalescing scalar columns (design D1).
  // ---------------------------------------------------------------------------
  await new Promise((r) => setTimeout(r, 1100)); // let updated_at advance
  const { data: replaced, error: replaceErr } = await a
    .from("pebble_drafts")
    .upsert(
      { id: draftId, user_id: alice.id, payload: { name: `renamed ${runId}` } },
      { onConflict: "id" },
    )
    .select("id, payload, created_at, updated_at")
    .single();
  check("upsert with an id replaces in place", !replaceErr && replaced?.id === draftId, replaceErr?.message);
  check("payload replaced wholesale (the emptied key is gone)",
    replaced?.payload?.name === `renamed ${runId}` && !("emotion_id" in (replaced?.payload ?? {})));
  check("created_at survives the replace", replaced?.created_at === quick?.created_at);
  check("updated_at advanced (trigger fires on upsert-as-update)",
    new Date(replaced!.updated_at as string) > new Date(quick!.updated_at as string),
    `${quick?.updated_at} -> ${replaced?.updated_at}`);
  check("still exactly one row", (await countRows(a, "pebble_drafts")) === 1);

  // ---------------------------------------------------------------------------
  // 3. RLS: owner-only, including through upsert.
  // ---------------------------------------------------------------------------
  const b = bob.client;
  const { data: bSel } = await b.from("pebble_drafts").select("id").eq("id", draftId);
  check("a second user cannot read the draft", (bSel?.length ?? -1) === 0, `got ${bSel?.length}`);

  const { data: bUpd } = await b
    .from("pebble_drafts").update({ payload: { name: "hijacked" } }).eq("id", draftId).select("id");
  check("a second user cannot update it", (bUpd?.length ?? -1) === 0, `affected ${bUpd?.length}`);

  const { data: bDel } = await b.from("pebble_drafts").delete().eq("id", draftId).select("id");
  check("a second user cannot delete it", (bDel?.length ?? -1) === 0, `affected ${bDel?.length}`);

  const { error: spoofInsert } = await b
    .from("pebble_drafts").insert({ user_id: alice.id, payload: { name: "spoof" } });
  check("`with check` rejects an insert owned by someone else", !!spoofInsert,
    spoofInsert ? `rejected ${spoofInsert.code}` : "ACCEPTED");

  // The one an UPDATE-without-`with check` policy would have let through.
  const { error: spoofUpsert } = await b
    .from("pebble_drafts")
    .upsert({ id: draftId, user_id: bob.id, payload: { name: "stolen" } }, { onConflict: "id" });
  check("upsert cannot re-own an existing row", !!spoofUpsert,
    spoofUpsert ? `rejected ${spoofUpsert.code}` : "ACCEPTED");

  const { data: intact } = await a.from("pebble_drafts").select("payload").eq("id", draftId).single();
  check("the draft survived every attempt intact", intact?.payload?.name === `renamed ${runId}`,
    JSON.stringify(intact?.payload));

  // ---------------------------------------------------------------------------
  // 4. Payload fidelity across surfaces. jsonb normalizes key ORDER, so compare
  //    semantically — what must not change is any value.
  // ---------------------------------------------------------------------------
  const snapId = crypto.randomUUID();
  const iosShaped = {
    name: "From iOS",
    description: null,
    happened_at: "2026-07-30T01:23:45Z", // iOS: whole seconds
    intensity: 3,
    positiveness: -1,
    visibility: "private",
    emotion_id: emotion.id,
    domain_ids: [domain.id],
    soul_ids: [] as string[],
    collection_ids: [] as string[],
    glyph_id: null,
    snaps: [{ id: snapId, storage_path: `${alice.id}/${snapId}`, sort_order: 0 }],
  };
  const { data: iosRow, error: iosErr } = await a
    .from("pebble_drafts")
    .upsert({ user_id: alice.id, payload: iosShaped }, { onConflict: "id" })
    .select("id, payload")
    .single();
  const stored = (iosRow?.payload ?? {}) as Record<string, unknown>;
  const sameValues = Object.entries(iosShaped).every(
    ([k, v]) => canonical(stored[k]) === canonical(v),
  );
  check("an iOS-shaped payload round-trips with no value loss", !iosErr && sameValues,
    iosErr?.message ?? JSON.stringify(stored));
  check("explicit nulls survive as null, not absent",
    stored.description === null && stored.glyph_id === null);
  check("empty arrays survive as arrays, not null",
    Array.isArray(stored.soul_ids) && (stored.soul_ids as unknown[]).length === 0);
  check("the nested snap descriptor survives intact",
    canonical(stored.snaps) === canonical(iosShaped.snaps),
    JSON.stringify(stored.snaps));

  // Web writes millisecond precision, which a fixed-precision ISO-8601 parser
  // silently drops (the #651 class of bug). Assert both that the DB preserves it
  // and that it really is the precision web produces.
  const webHappenedAt = new Date(Date.now() - 5 * 3600 * 1000).toISOString();
  check("web's toISOString really does carry milliseconds", /\.\d{3}Z$/.test(webHappenedAt), webHappenedAt);
  const webShaped = {
    name: `From web ${runId}`,
    happened_at: webHappenedAt,
    intensity: 2,
    positiveness: 0,
    visibility: "private",
    emotion_id: emotion.id,
    domain_ids: [domain.id],
  };
  const { data: webRow, error: webErr } = await a
    .from("pebble_drafts")
    .upsert({ user_id: alice.id, payload: webShaped }, { onConflict: "id" })
    .select("id, payload")
    .single();
  check("a web-shaped payload round-trips, milliseconds included",
    !webErr && webRow?.payload?.happened_at === webHappenedAt,
    `${webRow?.payload?.happened_at} vs ${webHappenedAt}`);

  // ---------------------------------------------------------------------------
  // 5. The drafts list contract: newest first.
  // ---------------------------------------------------------------------------
  const { data: listed } = await a
    .from("pebble_drafts").select("payload").order("updated_at", { ascending: false });
  check("list is ordered most-recently-saved first",
    (listed?.[0]?.payload as { name?: string })?.name === `From web ${runId}`,
    JSON.stringify(listed?.map((r) => (r.payload as { name?: string })?.name)));

  check("no karma yet, after every draft write so far", (await countRows(a, "karma_events")) === 0);

  // ---------------------------------------------------------------------------
  // 6. Publishing: the draft payload goes through the normal edge function once.
  // ---------------------------------------------------------------------------
  const { data: composed, error: composeErr } = await a.functions.invoke("compose-pebble", {
    body: { payload: webShaped },
  });
  const pebbleId = (composed as { pebble_id?: string })?.pebble_id;
  check("a draft payload publishes through compose-pebble", !composeErr && !!pebbleId,
    composeErr ? composeErr.message : JSON.stringify(composed));

  const { error: dropErr } = await a.from("pebble_drafts").delete().eq("id", webRow!.id as string);
  check("the published draft is then removable", !dropErr, dropErr?.message);

  check("exactly one pebble exists", (await countRows(a, "pebbles")) === 1);
  const { data: events } = await a.from("karma_events").select("reason, ref_id");
  const created = (events ?? []).filter((e) => e.reason === "pebble_created");
  check("exactly one pebble_created event", created.length === 1, JSON.stringify(events));
  check("it points at the published pebble", created[0]?.ref_id === pebbleId);

  const { data: pebble } = await a
    .from("pebbles").select("happened_at, render_svg").eq("id", pebbleId!).single();
  check("happened_at published verbatim, not re-stamped (design D6)",
    Math.abs(new Date(pebble!.happened_at as string).getTime() - new Date(webHappenedAt).getTime()) < 1000,
    `${pebble?.happened_at} vs ${webHappenedAt}`);
  check("render_svg composed on publish",
    typeof pebble?.render_svg === "string" && (pebble.render_svg as string).length > 0);

  // ---------------------------------------------------------------------------
  // 7. Purge covers pebble_drafts (the M46 standing rule).
  // ---------------------------------------------------------------------------
  const remaining = await countRows(a, "pebble_drafts");
  const res = await deleteAccount(alice.token);
  const body = await res.json().catch(() => ({}));
  check("delete-account responds 200 { ok: true }", res.status === 200 && body?.ok === true,
    `status=${res.status} body=${JSON.stringify(body)}`);
  check(`purge counts report pebble_drafts: ${remaining}`,
    body?.purged?.counts?.pebble_drafts === remaining,
    JSON.stringify(body?.purged?.counts));
  alice = null;
} catch (err) {
  failed += 1;
  console.error(`✗ aborted: ${err instanceof Error ? err.message : String(err)}`);
} finally {
  for (const [user, label] of [[alice, "alice"], [bob, "bob"]] as const) {
    if (!user) continue;
    const res = await deleteAccount(user.token).catch(() => null);
    console.log(`… cleanup ${label}: ${res ? res.status : "FAILED — remove drafts-verify-* manually"}`);
  }
}

console.log(`\nSummary: passed=${passed} failed=${failed}`);
Deno.exit(failed > 0 ? 1 : 0);
