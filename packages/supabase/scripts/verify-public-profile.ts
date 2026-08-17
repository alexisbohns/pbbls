#!/usr/bin/env -S deno run --allow-env --allow-net
/**
 * Acceptance test for the public-profile projection (M50) — runs against the
 * REMOTE project.
 *
 * `get_public_profile` is the schema's only anon-granted row-data function: a
 * `security definer` projection that reads one user's rows on behalf of a
 * caller who has no right to them, and is the standing template for every
 * future cross-user read. Two things therefore need proving end to end, and
 * neither can be proven from inside a single client's test suite:
 *
 *   1. The projection returns what it promises — and, since #688, that
 *      includes real achievements: unlocked only, most-recent first, capped at
 *      the shelf size, with the untruncated total beside them.
 *   2. The projection returns NOTHING else. The privacy contract is a key
 *      allowlist, so the assertion is on the exact key set, at both levels —
 *      a widening shows up as a new key long before it shows up as a bug.
 *
 * Run:
 *   SUPABASE_URL=... SUPABASE_ANON_KEY=... \
 *     deno run --allow-env --allow-net packages/supabase/scripts/verify-public-profile.ts
 *
 * Deliberately needs NO service-role key: it signs up throwaway users and
 * deletes them through the real `delete-account` edge function, so it is
 * runnable by anyone who can run the app. Cleanup runs even on failure.
 * Exits non-zero if any assertion fails.
 *
 * STANDING RULE: when the projection gains or loses a key, update
 * `PROFILE_KEYS` / `BADGE_KEYS` below in the same change. They are the
 * executable form of the function header's "deliberately excluded" list.
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

/** The complete public surface. Anything else appearing here is a leak. */
const PROFILE_KEYS = [
  "achievements",
  "achievements_count",
  "assiduity",
  "bounce_level",
  "days_practiced",
  "display_name",
  "glyph",
  "handle",
  "member_since",
  "pebbles_count",
  "ripple_level",
].sort();

/** The complete per-badge surface. */
const BADGE_KEYS = [
  "description_en",
  "description_fr",
  "domain_id",
  "emotion_id",
  "family",
  "glyph",
  "id",
  "slug",
  "threshold",
  "title_en",
  "title_fr",
  "unlocked_at",
].sort();

/** Named in the function header as never-projected; asserted, not trusted. */
const FORBIDDEN_KEYS = [
  "user_id",
  "id",
  "is_admin",
  "color_world",
  "karma",
  "active_today",
  "pebbles_28d",
  "active_days",
  "email",
  "terms_accepted_at",
  "privacy_accepted_at",
  "max_media_per_pebble",
  "public_profile",
];

const runId = crypto.randomUUID().slice(0, 8);
const password = `Public-${crypto.randomUUID()}`;
const handle = `pbv${runId}`;

type TestUser = { client: SupabaseClient; id: string; token: string };

async function signUp(label: string): Promise<TestUser> {
  const client = createClient(SUPABASE_URL!, ANON_KEY!, { auth: { persistSession: false } });
  const email = `public-verify-${label}-${runId}@example.test`;
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

type Badge = Record<string, unknown> & { slug: string };
type Projection = Record<string, unknown> & { achievements: Badge[]; achievements_count: number };

/**
 * The visitor's view: a brand-new client that never authenticates. Everything
 * asserted about the projection is asserted through THIS client — a signed-in
 * caller would prove nothing about the anon grant.
 */
const visitor = createClient(SUPABASE_URL, ANON_KEY, { auth: { persistSession: false } });

async function fetchPublic(h: string): Promise<Projection | null> {
  const { data, error } = await visitor.rpc("get_public_profile", { p_handle: h });
  if (error) throw new Error(`get_public_profile(${h}): ${error.message}`);
  return data as Projection | null;
}

let alice: TestUser | null = null;
let bob: TestUser | null = null;

try {
  alice = await signUp("a");
  bob = await signUp("b");
  console.log(`alice=${alice.id} handle=@${handle}\n`);
  const a = alice.client;

  const { data: emotions } = await a.from("emotions").select("id, slug").order("slug").limit(2);
  const { data: domain } = await a.from("domains").select("id, slug").order("slug").limit(1)
    .single();
  if (!emotions || emotions.length < 2 || !domain) {
    throw new Error("reference data missing (need 2 emotions and 1 domain)");
  }

  // ---------------------------------------------------------------------------
  // 0. Enumeration resistance: unknown and known-but-private are the same
  //    answer. Claim the handle first, leave public_profile false.
  // ---------------------------------------------------------------------------
  check("an unknown handle returns null", (await fetchPublic(`nobody${runId}`)) === null);

  const { error: handleErr } = await a.rpc("set_handle", { p_handle: handle });
  check("set_handle claims the handle", !handleErr, handleErr?.message);
  check(
    "a claimed but private handle is indistinguishable from an unknown one",
    (await fetchPublic(handle)) === null,
  );

  await a.from("profiles").update({ display_name: `Verifier ${runId}` }).eq("user_id", alice.id);
  const { error: pubErr } = await a
    .from("profiles").update({ public_profile: true }).eq("user_id", alice.id);
  check("the owner can opt in", !pubErr, pubErr?.message);

  // ---------------------------------------------------------------------------
  // 1. A profile with nothing unlocked still carries the keys. This is the
  //    day-one contract the placeholder existed to protect: a client that
  //    renders the shelf must never have to special-case "no badges yet".
  // ---------------------------------------------------------------------------
  const empty = await fetchPublic(handle);
  check("an opted-in profile is visible to an anonymous visitor", empty !== null);
  check(
    "achievements is an empty array, not null, when nothing is unlocked",
    Array.isArray(empty?.achievements) && empty!.achievements.length === 0,
    JSON.stringify(empty?.achievements),
  );
  check("achievements_count is 0", empty?.achievements_count === 0);

  // ---------------------------------------------------------------------------
  // 2. Seed a shelf. One create_pebble call carries the inline glyph, soul and
  //    collection, so a single pebble qualifies for five families at once; the
  //    second pebble adds a second emotion_first. Seven badges against a cap of
  //    six is the point — the slice has to actually cut something.
  // ---------------------------------------------------------------------------
  const basePebble = {
    happened_at: new Date().toISOString(),
    intensity: 2,
    positiveness: 1,
  };
  const { error: p1Err } = await a.rpc("create_pebble", {
    payload: {
      ...basePebble,
      name: `verify one ${runId}`,
      emotion_id: emotions[0].id,
      domain_ids: [domain.id],
      new_glyph: { name: `g ${runId}`, strokes: [], view_box: "0 0 100 100" },
      new_souls: [{ name: `s ${runId}` }],
      new_collections: [{ name: `c ${runId}` }],
    },
  });
  check("seed pebble 1 (glyph + soul + collection + domain)", !p1Err, p1Err?.message);

  const { error: p2Err } = await a.rpc("create_pebble", {
    payload: { ...basePebble, name: `verify two ${runId}`, emotion_id: emotions[1].id },
  });
  check("seed pebble 2 (a second emotion)", !p2Err, p2Err?.message);

  const { data: batch1, error: ackErr } = await a.rpc("check_achievements");
  check("the retroactive grant unlocks the whole history in one call", !ackErr, ackErr?.message);
  const unlockedSlugs = (batch1 as { slug: string }[] ?? []).map((r) => r.slug);
  check(
    `seven badges unlock (got ${unlockedSlugs.length}: ${unlockedSlugs.join(", ")})`,
    unlockedSlugs.length === 7,
  );

  // ---------------------------------------------------------------------------
  // 3. The projection itself.
  // ---------------------------------------------------------------------------
  const shelf = (await fetchPublic(handle))!;
  const slugs = shelf.achievements.map((b) => b.slug);

  check(
    "achievements_count reports the untruncated total",
    shelf.achievements_count === 7,
    `got ${shelf.achievements_count}`,
  );
  check(
    `the array is capped at the shelf size (got ${shelf.achievements.length})`,
    shelf.achievements.length === 6,
  );
  // Batch 1 shares one unlocked_at (one insert, one now()), so the whole slice
  // is decided by the sort_order tie-break: 100 pebble-count-1, 200 ×2
  // emotion_first, 300 domain_first, 400 first-collection, 410 first-glyph —
  // and 420 first-soul is the one that falls off.
  check(
    "the tie-break drops the highest sort_order, not an arbitrary row",
    !slugs.includes("first-soul") && slugs.includes("first-glyph"),
    slugs.join(", "),
  );
  check(
    "locked badges are never projected",
    slugs.every((s) => unlockedSlugs.includes(s)),
    slugs.join(", "),
  );

  const badge = shelf.achievements[0];
  check(
    "a badge carries exactly the projected keys",
    JSON.stringify(Object.keys(badge).sort()) === JSON.stringify(BADGE_KEYS),
    Object.keys(badge).sort().join(", "),
  );
  check(
    "unlocked_at is a UTC date, not a timestamp (no finer presence signal)",
    typeof badge.unlocked_at === "string" && /^\d{4}-\d{2}-\d{2}$/.test(badge.unlocked_at),
    String(badge.unlocked_at),
  );
  check(
    "seeded badges carry a null glyph until the admin assigns one",
    shelf.achievements.every((b) => b.glyph === null),
  );
  check(
    "the reference ids the clients localize by are projected",
    shelf.achievements.some((b) => b.family === "emotion_first" && typeof b.emotion_id === "string")
      && shelf.achievements.some((b) =>
        b.family === "domain_first" && typeof b.domain_id === "string"
      ),
  );

  // ---------------------------------------------------------------------------
  // 4. Recency wins over catalog order. A badge unlocked in a LATER call must
  //    displace older ones regardless of its sort_order — pebble-count-10 sorts
  //    at 110, behind almost everything in batch 1, and must still come first.
  // ---------------------------------------------------------------------------
  for (let i = 3; i <= 10; i += 1) {
    const { error } = await a.rpc("create_pebble", {
      payload: { ...basePebble, name: `verify ${i} ${runId}`, emotion_id: emotions[0].id },
    });
    if (error) throw new Error(`seed pebble ${i}: ${error.message}`);
  }
  const { data: batch2 } = await a.rpc("check_achievements");
  check(
    "a later call unlocks only what is newly earned",
    (batch2 as { slug: string }[] ?? []).map((r) => r.slug).join(",") === "pebble-count-10",
    JSON.stringify(batch2),
  );

  const recent = (await fetchPublic(handle))!;
  check(
    "the newest unlock leads the shelf, ahead of lower sort_order badges",
    recent.achievements[0]?.slug === "pebble-count-10",
    recent.achievements.map((b) => b.slug).join(", "),
  );
  check("the total grows with it", recent.achievements_count === 8);
  check("the array stays capped", recent.achievements.length === 6);

  // ---------------------------------------------------------------------------
  // 5. The privacy contract. The key allowlist is the whole mechanism, so it is
  //    asserted exactly rather than sampled.
  // ---------------------------------------------------------------------------
  check(
    "the projection carries exactly the public key set",
    JSON.stringify(Object.keys(recent).sort()) === JSON.stringify(PROFILE_KEYS),
    Object.keys(recent).sort().join(", "),
  );
  const leaked = FORBIDDEN_KEYS.filter((k) => k in recent);
  check(`no excluded key is projected`, leaked.length === 0, leaked.join(", "));

  // The badge payload must not reintroduce what the profile excludes.
  const badgeLeaks = shelf.achievements.flatMap((b) =>
    Object.keys(b).filter((k) => k === "user_id" || k === "karma_reward")
  );
  check("the badge payload adds no per-user or pricing keys", badgeLeaks.length === 0);

  // ---------------------------------------------------------------------------
  // 6. The definer read is not a widened RLS. Both must still be true after it.
  // ---------------------------------------------------------------------------
  const { data: bobPeek } = await bob.client
    .from("achievement_unlocks").select("achievement_id").eq("user_id", alice.id);
  check(
    "another signed-in user still cannot read the unlock ledger",
    (bobPeek ?? []).length === 0,
    JSON.stringify(bobPeek),
  );
  const { data: anonPeek } = await visitor.from("achievement_unlocks").select("achievement_id");
  check("an anonymous caller still cannot read the unlock ledger", (anonPeek ?? []).length === 0);

  const { data: anonGlyphs } = await visitor.from("glyphs").select("id").limit(1);
  check("glyphs RLS is not widened either", (anonGlyphs ?? []).length === 0);

  // ---------------------------------------------------------------------------
  // 7. Opting back out hides everything again, badges included.
  // ---------------------------------------------------------------------------
  await a.from("profiles").update({ public_profile: false }).eq("user_id", alice.id);
  check("opting out returns the profile to null", (await fetchPublic(handle)) === null);
} catch (err) {
  failed += 1;
  console.error(`✗ aborted: ${err instanceof Error ? err.message : String(err)}`);
} finally {
  for (const [user, label] of [[alice, "alice"], [bob, "bob"]] as const) {
    if (!user) continue;
    const res = await deleteAccount(user.token).catch(() => null);
    console.log(
      `… cleanup ${label}: ${res ? res.status : "FAILED — remove public-verify-* manually"}`,
    );
  }
}

console.log(`\nSummary: passed=${passed} failed=${failed}`);
Deno.exit(failed > 0 ? 1 : 0);
