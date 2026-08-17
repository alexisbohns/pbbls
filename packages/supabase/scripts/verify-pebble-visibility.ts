#!/usr/bin/env -S deno run --allow-env --allow-net
/**
 * Acceptance test for privacy grades (M51) — runs against the REMOTE project.
 *
 * The visibility contract crosses every surface: the same `pebbles_select`
 * policy answers web, iOS and Android, and `get_shared_pebble` answers
 * anonymous visitors. No single app's test suite can prove the matrix, so
 * this exercises it against the real database and edge functions:
 *
 *   1. Defaults — a payload without a grade lands 'secret' (column default and
 *      the create_pebble coalesce both flipped from 'private').
 *   2. CHECK — an invalid grade is unrepresentable.
 *   3. Read matrix — owner sees all grades; a mutual connection sees
 *      'private' + 'public' but never 'secret'; a stranger sees 'public'
 *      only; anon sees nothing through the table.
 *   4. Enrichment fence — v_pebbles_full shows a connection the shared row
 *      with EMPTY cards/souls/snaps (enrichment RLS stays owner-only).
 *   5. Writes stay owner-only — a viewer cannot re-grade someone else's
 *      pebble.
 *   6. get_shared_pebble — anon gets the projection for 'public' (no user_id,
 *      whole-second UTC happened_at, composed render_svg); 'secret',
 *      'private' and unknown ids are all indistinguishably null.
 *
 * Run:
 *   SUPABASE_URL=... SUPABASE_ANON_KEY=... \
 *     deno run --allow-env --allow-net packages/supabase/scripts/verify-pebble-visibility.ts
 *
 * Deliberately needs NO service-role key: it signs up throwaway users and
 * deletes them through the real `delete-account` edge function, so it is
 * runnable by anyone who can run the app. Cleanup runs even on failure.
 * Exits non-zero if any assertion fails.
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
const password = `Grades-${crypto.randomUUID()}`;

type TestUser = { client: SupabaseClient; id: string; token: string };

async function signUp(label: string): Promise<TestUser> {
  const client = createClient(SUPABASE_URL!, ANON_KEY!, { auth: { persistSession: false } });
  const email = `grades-verify-${label}-${runId}@example.test`;
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

let owner: TestUser | null = null;
let friend: TestUser | null = null;
let stranger: TestUser | null = null;

try {
  owner = await signUp("owner");
  friend = await signUp("friend");
  stranger = await signUp("stranger");
  console.log(`owner=${owner.id} friend=${friend.id} stranger=${stranger.id}\n`);
  const o = owner.client;
  const f = friend.client;
  const s = stranger.client;
  // A session-less client: exactly what the /p/[id] page holds for a visitor.
  const anon = createClient(SUPABASE_URL!, ANON_KEY!, { auth: { persistSession: false } });

  const { data: emotion } = await o.from("emotions").select("id").limit(1).single();
  const { data: cardType } = await o.from("card_types").select("id").limit(1).single();
  if (!emotion || !cardType) throw new Error("reference data missing (emotion / card type)");

  // ---------------------------------------------------------------------------
  // 0. owner↔friend become a mutual connection through the real M49 path.
  // ---------------------------------------------------------------------------
  const { data: invite, error: inviteErr } = await o.rpc("create_connection_invite");
  const token = (invite as { token?: string } | null)?.token;
  if (inviteErr || !token) throw new Error(`create_connection_invite: ${inviteErr?.message}`);
  const { error: acceptErr } = await f.rpc("accept_connection_invite", { p_token: token });
  if (acceptErr) throw new Error(`accept_connection_invite: ${acceptErr.message}`);

  // ---------------------------------------------------------------------------
  // 1. Three pebbles, one per grade. The 'secret' one sends NO visibility key
  //    (that is the default under test); the 'public' one goes through
  //    compose-pebble so render_svg is really composed. The 'private' one
  //    carries a card + a soul so the enrichment fence has something to hide.
  // ---------------------------------------------------------------------------
  const base = {
    happened_at: "2026-08-01T12:00:00Z",
    intensity: 2,
    positiveness: 1,
    emotion_id: emotion.id,
  };

  const { data: secretId, error: secretErr } = await o.rpc("create_pebble", {
    payload: { ...base, name: `secret ${runId}` },
  });
  check("create_pebble without a grade succeeds", !secretErr && !!secretId, secretErr?.message);

  const { data: privateId, error: privateErr } = await o.rpc("create_pebble", {
    payload: {
      ...base,
      name: `private ${runId}`,
      visibility: "private",
      cards: [{ species_id: cardType.id, value: "owner-only card", sort_order: 0 }],
      new_souls: [{ name: `soul ${runId}` }],
    },
  });
  check("create_pebble with 'private' succeeds", !privateErr && !!privateId, privateErr?.message);

  const { data: composed, error: composeErr } = await o.functions.invoke("compose-pebble", {
    body: { payload: { ...base, name: `public ${runId}`, visibility: "public" } },
  });
  const publicId = (composed as { pebble_id?: string })?.pebble_id;
  check("compose-pebble with 'public' succeeds", !composeErr && !!publicId,
    composeErr ? composeErr.message : JSON.stringify(composed));

  const { data: secretRow } = await o
    .from("pebbles").select("visibility").eq("id", secretId as string).single();
  check("a grade-less payload lands 'secret' (default flipped)",
    secretRow?.visibility === "secret", secretRow?.visibility);

  const { error: badGradeErr } = await o.rpc("create_pebble", {
    payload: { ...base, name: `bad ${runId}`, visibility: "friends" },
  });
  check("an invalid grade is rejected by the CHECK", !!badGradeErr, "insert succeeded");

  // ---------------------------------------------------------------------------
  // 2. The read matrix over the pebbles table.
  // ---------------------------------------------------------------------------
  const gradeOf = (rows: { id: string }[] | null, id: unknown) =>
    (rows ?? []).some((r) => r.id === id);

  const { data: ownRows } = await o.from("pebbles").select("id");
  check("owner sees all three grades", ownRows?.length === 3, `${ownRows?.length}`);

  const { data: friendRows } = await f.from("pebbles").select("id");
  check("connection sees the private pebble", gradeOf(friendRows, privateId));
  check("connection sees the public pebble", gradeOf(friendRows, publicId));
  check("connection never sees the secret pebble", !gradeOf(friendRows, secretId),
    JSON.stringify(friendRows));

  const { data: strangerRows } = await s.from("pebbles").select("id");
  check("stranger sees the public pebble", gradeOf(strangerRows, publicId));
  check("stranger sees neither secret nor private",
    !gradeOf(strangerRows, secretId) && !gradeOf(strangerRows, privateId),
    JSON.stringify(strangerRows));

  const { data: anonRows, error: anonErr } = await anon.from("pebbles").select("id");
  check("anon reads nothing through the table (grant revoked)",
    !!anonErr || (anonRows ?? []).length === 0,
    JSON.stringify(anonRows));

  // ---------------------------------------------------------------------------
  // 3. Enrichment fence: the connection gets the row, never the trimmings.
  // ---------------------------------------------------------------------------
  const { data: fullRow } = await f
    .from("v_pebbles_full").select("id, name, cards, souls, snaps")
    .eq("id", privateId as string).maybeSingle();
  check("v_pebbles_full returns the shared row to a connection", !!fullRow, "row missing");
  check("…with empty enrichments (cards/souls/snaps RLS stays owner-only)",
    (fullRow?.cards as unknown[])?.length === 0 &&
    (fullRow?.souls as unknown[])?.length === 0 &&
    (fullRow?.snaps as unknown[])?.length === 0,
    JSON.stringify({ cards: fullRow?.cards, souls: fullRow?.souls, snaps: fullRow?.snaps }));

  // ---------------------------------------------------------------------------
  // 4. Reading is not writing: a viewer cannot re-grade someone else's pebble.
  // ---------------------------------------------------------------------------
  await f.from("pebbles").update({ visibility: "public" }).eq("id", secretId as string);
  await s.from("pebbles").update({ visibility: "secret" }).eq("id", publicId as string);
  const { data: afterWrites } = await o
    .from("pebbles").select("id, visibility").in("id", [secretId, publicId] as string[]);
  check("non-owner updates affect zero rows",
    afterWrites?.find((r) => r.id === secretId)?.visibility === "secret" &&
    afterWrites?.find((r) => r.id === publicId)?.visibility === "public",
    JSON.stringify(afterWrites));

  // ---------------------------------------------------------------------------
  // 5. get_shared_pebble: the anonymous share-by-link projection.
  // ---------------------------------------------------------------------------
  const { data: shared, error: sharedErr } = await anon.rpc("get_shared_pebble", {
    p_pebble_id: publicId,
  });
  const sharedObj = shared as Record<string, unknown> | null;
  check("anon resolves a public pebble by link", !sharedErr && !!sharedObj, sharedErr?.message);
  check("projection carries name + emotion + palette",
    sharedObj?.name === `public ${runId}` &&
    typeof (sharedObj?.emotion as Record<string, unknown>)?.color === "string" &&
    typeof (sharedObj?.emotion as Record<string, unknown>)?.primary_color === "string",
    JSON.stringify(sharedObj));
  check("projection carries the composed render_svg",
    typeof sharedObj?.render_svg === "string" && (sharedObj.render_svg as string).length > 0);
  check("projection never leaks user_id", sharedObj !== null && !("user_id" in sharedObj));
  check("happened_at is whole-second UTC (standing timestamp rule)",
    sharedObj?.happened_at === "2026-08-01T12:00:00Z", `${sharedObj?.happened_at}`);

  for (const [label, id] of [
    ["secret", secretId],
    ["private", privateId],
    ["unknown", crypto.randomUUID()],
  ] as const) {
    const { data: hidden, error: hiddenErr } = await anon.rpc("get_shared_pebble", {
      p_pebble_id: id,
    });
    check(`a ${label} id is indistinguishably null`, !hiddenErr && hidden === null,
      hiddenErr?.message ?? JSON.stringify(hidden));
  }

  const { data: authShared } = await f.rpc("get_shared_pebble", { p_pebble_id: publicId });
  check("the link also resolves for signed-in visitors", !!authShared);
} catch (err) {
  failed += 1;
  console.error(`✗ aborted: ${err instanceof Error ? err.message : String(err)}`);
} finally {
  for (const [user, label] of [
    [owner, "owner"],
    [friend, "friend"],
    [stranger, "stranger"],
  ] as const) {
    if (!user) continue;
    const res = await deleteAccount(user.token).catch(() => null);
    console.log(`… cleanup ${label}: ${res ? res.status : "FAILED — remove grades-verify-* manually"}`);
  }
}

console.log(`\nSummary: passed=${passed} failed=${failed}`);
Deno.exit(failed > 0 ? 1 : 0);
