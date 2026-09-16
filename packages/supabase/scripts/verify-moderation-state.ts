#!/usr/bin/env -S deno run --allow-env --allow-net
/**
 * Acceptance test for the moderation state (#833, M56) — runs against the
 * REMOTE project.
 *
 * `hidden_at` replaced #832's destructive takedown. The property it has to
 * hold is a MATRIX, not a single row read: hidden content must be dark on
 * every cross-user and anonymous path, still visible to its own author, and
 * revocable by moderation but NOT by its subject. No client test suite can
 * prove that — the same policy answers web, iOS, Android and the anon share
 * link — so it is proven here against the real RLS policies, triggers and
 * RPCs.
 *
 * Why this one needs the SERVICE ROLE (and therefore is NOT in the CI gate):
 * setting `hidden_at` requires an admin, and `profiles_privileged_guard` pins
 * `is_admin` against every client write. Only postgres / service_role / a
 * definer function can grant it. So the moderator is minted the same way
 * verify-account-purge.ts mints its own: sign up with the anon key, then
 * promote out of band with the service role. The admin RPCs read auth.uid(),
 * so they are called through the moderator's OWN session, never through the
 * service-role client.
 *
 * It sits beside db:verify:purge as a manual run:
 *
 *   SUPABASE_URL=... SUPABASE_ANON_KEY=... SUPABASE_SERVICE_ROLE_KEY=... \
 *     deno run --allow-env --allow-net packages/supabase/scripts/verify-moderation-state.ts
 *
 * Every account it creates is deleted in a `finally` (the real delete-account
 * edge function first, service-role force-delete as the backstop) — including
 * the moderator: a stray admin account is a security smell to leave behind.
 * Exits non-zero if any assertion fails.
 */

import { createClient, type SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL");
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY");
const SERVICE_ROLE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");

if (!SUPABASE_URL || !ANON_KEY || !SERVICE_ROLE) {
  console.error("SUPABASE_URL, SUPABASE_ANON_KEY and SUPABASE_SERVICE_ROLE_KEY must be set");
  Deno.exit(2);
}

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

const runId = crypto.randomUUID().slice(0, 8);
const password = `Moderation-${crypto.randomUUID()}`;

type TestUser = { client: SupabaseClient; id: string; token: string; label: string };

async function signUp(label: string): Promise<TestUser> {
  const client = createClient(SUPABASE_URL!, ANON_KEY!, { auth: { persistSession: false } });
  const email = `moderation-verify-${label}-${runId}@example.test`;
  const { data, error } = await client.auth.signUp({ email, password });
  if (error || !data.session || !data.user) {
    throw new Error(`signUp ${label}: ${error?.message ?? "no session (email confirmations on?)"}`);
  }
  return { client, id: data.user.id, token: data.session.access_token, label };
}

/**
 * A real authenticated admin session. is_admin is pinned against client writes
 * by profiles_privileged_guard (20260902090000) and now also carries the
 * moderation columns (20260916100000); the exemption is exactly this — the
 * service role sets it out of band.
 */
async function mintModerator(): Promise<TestUser> {
  const user = await signUp("mod");
  const { error } = await admin
    .from("profiles").update({ is_admin: true }).eq("user_id", user.id);
  if (error) throw new Error(`promote moderator: ${error.message}`);
  return user;
}

/** The real client teardown path. */
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
 * Backstop: the edge function can fail for reasons that have nothing to do
 * with this harness, and a leftover account — above all the ADMIN one — must
 * not survive the run. Reports what it found so the run's own output is the
 * evidence that nothing was left behind.
 */
async function forceCleanup(user: TestUser | null) {
  if (!user) return;
  const { data } = await admin.auth.admin.getUserById(user.id);
  if (!data?.user) {
    console.log(`… cleanup ${user.label}: auth user gone (verified via service role)`);
    return;
  }
  console.log(`… cleanup ${user.label}: still present — force-cleaning via service role`);
  await admin.rpc("purge_account", { p_user_id: user.id });
  await admin.auth.admin.deleteUser(user.id);
  const { data: after } = await admin.auth.admin.getUserById(user.id);
  console.log(`… cleanup ${user.label}: ${after?.user ? "STILL PRESENT — remove manually" : "gone"}`);
}

let owner: TestUser | null = null;
let connection: TestUser | null = null;
let stranger: TestUser | null = null;
let moderator: TestUser | null = null;

try {
  owner = await signUp("owner");
  connection = await signUp("connection");
  stranger = await signUp("stranger");
  moderator = await mintModerator();
  console.log(
    `owner=${owner.id} connection=${connection.id} stranger=${stranger.id} moderator=${moderator.id}\n`,
  );
  const o = owner.client;
  const c = connection.client;
  const s = stranger.client;
  const m = moderator.client;
  // Session-less: exactly what the /p/[id] share page holds for a visitor.
  const anon = createClient(SUPABASE_URL!, ANON_KEY!, { auth: { persistSession: false } });

  const { data: emotion } = await o.from("emotions").select("id").limit(1).single();
  if (!emotion) throw new Error("reference data missing (emotion)");

  // ---------------------------------------------------------------------------
  // 0. owner ↔ connection become mutual through the real M49 path, so the
  //    `private` arm of pebbles_select is exercised rather than simulated.
  // ---------------------------------------------------------------------------
  const { data: invite, error: inviteErr } = await o.rpc("create_connection_invite");
  const inviteToken = (invite as { token?: string } | null)?.token;
  if (inviteErr || !inviteToken) throw new Error(`create_connection_invite: ${inviteErr?.message}`);
  const { error: acceptErr } = await c.rpc("accept_connection_invite", { p_token: inviteToken });
  if (acceptErr) throw new Error(`accept_connection_invite: ${acceptErr.message}`);

  // ---------------------------------------------------------------------------
  // 1. Seed: a public pebble, a private pebble, a second public pebble for the
  //    report path, and a published profile.
  // ---------------------------------------------------------------------------
  const base = {
    happened_at: "2026-09-01T12:00:00Z",
    intensity: 2,
    positiveness: 1,
    emotion_id: emotion.id,
  };

  const { data: publicId, error: publicErr } = await o.rpc("create_pebble", {
    payload: { ...base, name: `moderation public ${runId}`, visibility: "public" },
  });
  if (publicErr || !publicId) throw new Error(`create public pebble: ${publicErr?.message}`);

  const { data: privateId, error: privateErr } = await o.rpc("create_pebble", {
    payload: { ...base, name: `moderation private ${runId}`, visibility: "private" },
  });
  if (privateErr || !privateId) throw new Error(`create private pebble: ${privateErr?.message}`);

  const { data: reportedId, error: reportedErr } = await o.rpc("create_pebble", {
    payload: { ...base, name: `moderation reported ${runId}`, visibility: "public" },
  });
  if (reportedErr || !reportedId) throw new Error(`create reported pebble: ${reportedErr?.message}`);

  const handle = `modverify${runId}`;
  const { error: handleErr } = await o.rpc("set_handle", { p_handle: handle });
  if (handleErr) throw new Error(`set_handle: ${handleErr.message}`);
  const { error: publishErr } = await o
    .from("profiles").update({ public_profile: true }).eq("user_id", owner.id);
  if (publishErr) throw new Error(`publish profile: ${publishErr.message}`);

  // Helpers used on both sides of the hide/un-hide transition, so "restored"
  // is compared against the same probes that proved "dark".
  const seesPebble = async (client: SupabaseClient, id: string) => {
    const { data } = await client.from("pebbles").select("id").eq("id", id).maybeSingle();
    return !!data;
  };
  const seesInFullView = async (client: SupabaseClient, id: string) => {
    const { data } = await client.from("v_pebbles_full").select("id").eq("id", id).maybeSingle();
    return !!data;
  };
  const sharedByLink = async (id: string) => {
    const { data, error } = await anon.rpc("get_shared_pebble", { p_pebble_id: id });
    if (error) throw new Error(`get_shared_pebble: ${error.message}`);
    return data as Record<string, unknown> | null;
  };

  // ---------------------------------------------------------------------------
  // 2. Baseline. Without this, "hidden" below could be proven by a seed that
  //    was never visible in the first place.
  // ---------------------------------------------------------------------------
  check("baseline: owner sees their public pebble", await seesPebble(o, publicId as string));
  check("baseline: stranger sees the public pebble", await seesPebble(s, publicId as string));
  check("baseline: connection sees the public pebble", await seesPebble(c, publicId as string));
  check("baseline: connection sees the private pebble", await seesPebble(c, privateId as string));
  check("baseline: the public pebble is in v_pebbles_full for the stranger",
    await seesInFullView(s, publicId as string));
  check("baseline: anon resolves the public pebble by link",
    (await sharedByLink(publicId as string)) !== null);

  // ---------------------------------------------------------------------------
  // 3. Hide the public pebble through the admin RPC (the moderator's OWN
  //    session — admin_set_content_hidden reads auth.uid()).
  // ---------------------------------------------------------------------------
  const { error: hideErr } = await m.rpc("admin_set_content_hidden", {
    p_target_kind: "pebble",
    p_target_id: publicId,
    p_hidden: true,
    p_note: "moderation harness",
  });
  check("admin_set_content_hidden hides a pebble", !hideErr, hideErr?.message);

  const { data: hiddenRow } = await admin
    .from("pebbles").select("hidden_at, hidden_by, visibility").eq("id", publicId as string)
    .maybeSingle();
  check("hidden_at is stamped and attributed to the moderator",
    !!hiddenRow?.hidden_at && hiddenRow?.hidden_by === moderator.id,
    JSON.stringify(hiddenRow));

  // ---------------------------------------------------------------------------
  // 3b. THE GUARD, against a GENUINELY hidden row.
  //
  // This moved here from the anon harness, which structurally cannot prove it:
  // both guards fire on `new.hidden_at is distinct from old.hidden_at`, and
  // null-to-null is NOT distinct — so clearing a flag that was never set is an
  // idempotent no-op the guard allows. Only once something is actually hidden
  // does the CLEAR case become testable.
  //
  // Match the MESSAGE, not merely "an error happened": a dropped grant, an
  // expired session, a renamed column and an RLS-filtered zero-row update all
  // produce an error (or none) too, and those are exactly the states a careless
  // refactor produces. Only the guard's own exception name proves the guard did
  // the refusing. Without this assertion, moderation is revocable by its own
  // subject and the whole feature is theatre.
  // ---------------------------------------------------------------------------
  const { error: ownerUnhideErr } = await o
    .from("pebbles").update({ hidden_at: null }).eq("id", publicId as string);
  check("the owner CANNOT clear hidden_at on their own hidden pebble",
    ownerUnhideErr?.message?.includes("pebbles_moderation_column") === true,
    `expected pebbles_moderation_column, got: ${ownerUnhideErr?.message ?? "no error at all"}` +
      ` (code=${ownerUnhideErr?.code ?? "none"})`);

  const { data: stillHidden } = await admin
    .from("pebbles").select("hidden_at").eq("id", publicId as string).maybeSingle();
  check("…and the refused update left the pebble hidden", !!stillHidden?.hidden_at,
    JSON.stringify(stillHidden));

  // ---------------------------------------------------------------------------
  // 4. The visibility matrix while hidden.
  // ---------------------------------------------------------------------------
  // D2, the anti-data-loss property: hiding a pebble from its AUTHOR would
  // present as the app eating their entry. Suspending someone's public reach
  // and deleting their journal must not look the same from inside the app.
  check("the owner STILL sees their own hidden pebble (table)",
    await seesPebble(o, publicId as string),
    "the owner arm of pebbles_select was gated — this presents as data loss");
  check("the owner STILL sees their own hidden pebble (v_pebbles_full)",
    await seesInFullView(o, publicId as string),
    "the owner arm of pebbles_select was gated — this presents as data loss");

  check("a stranger does not see the hidden pebble (public arm)",
    !(await seesPebble(s, publicId as string)));
  check("a mutual connection does not see the hidden pebble",
    !(await seesPebble(c, publicId as string)));
  check("anon get_shared_pebble returns null for the hidden pebble",
    (await sharedByLink(publicId as string)) === null);
  // Proves the view inherits the policy rather than assuming it: v_pebbles_full
  // is security_invoker, so pebbles_select applies through it. A view created
  // without that option would still pass every table assertion above while
  // leaking the row here.
  check("the hidden pebble is absent from v_pebbles_full for the stranger",
    !(await seesInFullView(s, publicId as string)),
    "v_pebbles_full did not inherit pebbles_select (security_invoker?)");

  // The private arm has its own predicate; a hidden PUBLIC pebble never
  // reaches it. Hide the private one too so the arm a connection reads through
  // is proven dark as well.
  const { error: hidePrivateErr } = await m.rpc("admin_set_content_hidden", {
    p_target_kind: "pebble",
    p_target_id: privateId,
    p_hidden: true,
  });
  check("admin_set_content_hidden hides the private pebble", !hidePrivateErr,
    hidePrivateErr?.message);
  check("a mutual connection loses a hidden PRIVATE pebble (private arm)",
    !(await seesPebble(c, privateId as string)));
  check("the owner still sees their own hidden private pebble",
    await seesPebble(o, privateId as string));

  // ---------------------------------------------------------------------------
  // 5. The APPEAL PATH — un-hiding restores every path. This is what #832's
  //    destructive takedown made impossible.
  // ---------------------------------------------------------------------------
  for (const [label, id] of [["public", publicId], ["private", privateId]] as const) {
    const { error } = await m.rpc("admin_set_content_hidden", {
      p_target_kind: "pebble",
      p_target_id: id,
      p_hidden: false,
      p_note: "appeal upheld",
    });
    check(`admin_set_content_hidden un-hides the ${label} pebble`, !error, error?.message);
  }

  const { data: clearedRow } = await admin
    .from("pebbles").select("hidden_at, hidden_by").eq("id", publicId as string).maybeSingle();
  check("un-hiding clears both moderation columns",
    clearedRow?.hidden_at === null && clearedRow?.hidden_by === null,
    JSON.stringify(clearedRow));

  check("restored: the stranger sees the public pebble again",
    await seesPebble(s, publicId as string));
  check("restored: the connection sees the public pebble again",
    await seesPebble(c, publicId as string));
  check("restored: the connection sees the private pebble again",
    await seesPebble(c, privateId as string));
  check("restored: the public pebble is back in v_pebbles_full for the stranger",
    await seesInFullView(s, publicId as string));
  check("restored: anon resolves the public pebble by link again",
    (await sharedByLink(publicId as string)) !== null);

  // ---------------------------------------------------------------------------
  // 6. Hidden PROFILE: the public projection goes dark, the handle does not
  //    come free.
  // ---------------------------------------------------------------------------
  const { data: livePublic, error: livePublicErr } = await s
    .rpc("get_public_profile", { p_handle: handle });
  check("baseline: the published profile resolves via get_public_profile",
    !livePublicErr && !!livePublic, livePublicErr?.message ?? JSON.stringify(livePublic));

  const { error: hideProfileErr } = await m.rpc("admin_set_content_hidden", {
    p_target_kind: "profile",
    p_target_id: owner.id,
    p_hidden: true,
    p_note: "moderation harness",
  });
  check("admin_set_content_hidden hides a profile", !hideProfileErr, hideProfileErr?.message);

  const { data: darkProfile, error: darkProfileErr } = await s
    .rpc("get_public_profile", { p_handle: handle });
  check("a hidden profile resolves null from get_public_profile",
    !darkProfileErr && darkProfile === null,
    darkProfileErr?.message ?? JSON.stringify(darkProfile));

  // The guard on the profiles side, again against a genuinely hidden row.
  const { error: ownerProfileUnhideErr } = await o
    .from("profiles").update({ hidden_at: null }).eq("user_id", owner.id);
  check("the owner CANNOT clear hidden_at on their own hidden profile",
    ownerProfileUnhideErr?.message?.includes("profiles_privileged_column") === true,
    `expected profiles_privileged_column, got: ${ownerProfileUnhideErr?.message ?? "no error at all"}` +
      ` (code=${ownerProfileUnhideErr?.code ?? "none"})`);

  const { data: profileStillHidden } = await admin
    .from("profiles").select("hidden_at, handle").eq("user_id", owner.id).maybeSingle();
  check("…and the refused update left the profile hidden", !!profileStillHidden?.hidden_at,
    JSON.stringify(profileStillHidden));

  // D6: hiding is not releasing. The name stays claimed — otherwise a
  // suspension would hand the handle to whoever asked next, including the
  // person who got it suspended.
  check("a hidden profile still HOLDS its handle in the row",
    profileStillHidden?.handle === handle, String(profileStillHidden?.handle));
  const { error: stolenErr } = await s.rpc("set_handle", { p_handle: handle });
  check("another user CANNOT claim a hidden profile's handle",
    stolenErr?.message?.includes("handle_taken") === true,
    `expected handle_taken, got: ${stolenErr?.message ?? "the handle was claimed"}`);

  // ---------------------------------------------------------------------------
  // 7. admin_release_handle — the destructive half, now explicit and separate.
  // ---------------------------------------------------------------------------
  const { data: released, error: releaseErr } = await m.rpc("admin_release_handle", {
    p_user_id: owner.id,
    p_note: "impersonation",
  });
  check("admin_release_handle returns the freed handle",
    !releaseErr && (released as { released_handle?: string } | null)?.released_handle === handle,
    releaseErr?.message ?? JSON.stringify(released));

  const { data: releasedRow } = await admin
    .from("profiles").select("handle, public_profile").eq("user_id", owner.id).maybeSingle();
  check("the released profile keeps no handle and is unpublished",
    releasedRow?.handle === null && releasedRow?.public_profile === false,
    JSON.stringify(releasedRow));

  const { error: claimErr } = await s.rpc("set_handle", { p_handle: handle });
  check("after admin_release_handle another user CAN claim it", !claimErr, claimErr?.message);

  // ---------------------------------------------------------------------------
  // 8. resolve_content_report('actioned') hides instead of re-grading.
  //
  // THE assertion that proves #833 actually replaced #832's destructive
  // behaviour: the previous emission overwrote pebbles.visibility with
  // 'secret', destroying a setting the user owns and making the takedown
  // unappealable. hidden_at must be stamped and visibility must be untouched.
  // ---------------------------------------------------------------------------
  const { data: report, error: reportErr } = await s.rpc("report_content", {
    p_target_kind: "pebble",
    p_target_id: reportedId,
    p_reason: "harassment",
    p_detail: "moderation harness",
  });
  const reportId = (report as { id?: string } | null)?.id;
  check("a stranger can report the owner's public pebble", !reportErr && !!reportId,
    reportErr?.message);

  const { error: resolveErr } = await m.rpc("resolve_content_report", {
    p_report_id: reportId,
    p_outcome: "actioned",
    p_note: "harness takedown",
  });
  check("resolve_content_report('actioned') succeeds", !resolveErr, resolveErr?.message);

  const { data: actioned } = await admin
    .from("pebbles").select("hidden_at, hidden_by, visibility").eq("id", reportedId as string)
    .maybeSingle();
  check("actioning a pebble report SETS hidden_at", !!actioned?.hidden_at,
    JSON.stringify(actioned));
  check("actioning a pebble report leaves visibility UNCHANGED at 'public'",
    actioned?.visibility === "public",
    `visibility is '${actioned?.visibility}' — the destructive takedown is back`);
  check("the actioned takedown is attributed to the moderator",
    actioned?.hidden_by === moderator.id, String(actioned?.hidden_by));
  check("the actioned pebble is dark to a stranger",
    !(await seesPebble(s, reportedId as string)));
  check("the owner still sees their actioned pebble",
    await seesPebble(o, reportedId as string));
} catch (err) {
  failed += 1;
  console.error(`✗ aborted: ${err instanceof Error ? err.message : String(err)}`);
} finally {
  for (const user of [owner, connection, stranger, moderator]) {
    if (!user) continue;
    const res = await deleteAccount(user.token).catch(() => null);
    console.log(`… delete-account ${user.label}: ${res ? res.status : "request failed"}`);
  }
  try {
    for (const user of [owner, connection, stranger, moderator]) {
      await forceCleanup(user);
    }
  } catch (err) {
    console.error(`cleanup failed — remove moderation-verify-* users manually: ${err}`);
  }
}

console.log(`\nSummary: passed=${passed} failed=${failed}`);
Deno.exit(failed > 0 ? 1 : 0);
