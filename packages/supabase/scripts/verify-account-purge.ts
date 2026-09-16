#!/usr/bin/env -S deno run --allow-env --allow-net
/**
 * Acceptance test for account deletion (M46) — runs against the REMOTE project.
 *
 * Seeds a throwaway SELLER with every entity type (profile, soul, collection,
 * pebble + cards/domains/soul-link/collection-link/snap, storage files, a SOLD
 * glyph bought by a throwaway BUYER, an unsold glyph, favourites, karma,
 * achievement unlocks), then
 * deletes the seller through the real delete-account edge function and asserts
 * the roadmap §6 bar: every seller row gone, the buyer's glyph still renders
 * (user_id = null + entitlement + delisted-but-approved submission), the
 * storage prefix is empty, the auth user is gone, and a purge_account re-run
 * converges to zero counts. The buyer is then deleted through the same edge
 * path (dogfoods the buyer-side purge: own entitlement before the purchase
 * karma_event it references).
 *
 * Run:
 *   SUPABASE_URL=... SUPABASE_SERVICE_ROLE_KEY=... SUPABASE_ANON_KEY=... \
 *     deno run --allow-env --allow-net packages/supabase/scripts/verify-account-purge.ts
 *
 * Safety: every statement is scoped to the two user ids minted by THIS run.
 * Cleanup runs even on failure (service-role purge + deleteUser fallback).
 * Exits non-zero if any assertion fails.
 *
 * STANDING RULE (roadmap §M46): when a later milestone adds a user-owned
 * table to purge_account, extend the seed + zero-row assertions here too.
 *
 * It also carries the content_reports assertions that verify-content-reports.ts
 * structurally cannot (#831): that harness is anon-only, so it can neither read
 * the table back nor mint an admin past profiles_privileged_guard. Here, the
 * service role does both — so this is where "target_user_id is resolved
 * correctly", "a LISTED glyph is reportable" and the takedown dispatch of
 * resolve_content_report are actually proven.
 */

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL");
const SERVICE_ROLE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY");

if (!SUPABASE_URL || !SERVICE_ROLE || !ANON_KEY) {
  console.error("SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY and SUPABASE_ANON_KEY must be set");
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

async function countRows(table: string, column: string, value: string): Promise<number> {
  const { count, error } = await admin
    .from(table)
    .select("*", { count: "exact", head: true })
    .eq(column, value);
  if (error) throw new Error(`count ${table}.${column} failed: ${error.message}`);
  return count ?? 0;
}

async function listStorageFiles(prefix: string): Promise<string[]> {
  const files: string[] = [];
  const dirs = [prefix];
  while (dirs.length > 0) {
    const dir = dirs.pop() as string;
    const { data, error } = await admin.storage.from("pebbles-media").list(dir, { limit: 100 });
    if (error) throw new Error(`storage list ${dir} failed: ${error.message}`);
    for (const entry of data ?? []) {
      if (entry.id === null) dirs.push(`${dir}/${entry.name}`);
      else files.push(`${dir}/${entry.name}`);
    }
  }
  return files;
}

// A named, non-generic factory (rather than inlining `createClient(...)` at
// each call site) so its return type can be used as a concrete parameter/
// return type elsewhere in this file. `ReturnType<typeof createClient>` looks
// equivalent but is NOT: createClient is itself generic, so that alias loses
// the Database-shaped overload resolution and every `.rpc(name, args)` call
// through it type-checks `args` against `undefined` instead of the real Args
// type — deno check (and `deno run`, which type-checks by default) fails.
function createAnonClient() {
  return createClient(SUPABASE_URL!, ANON_KEY!, { auth: { persistSession: false } });
}

/**
 * A real authenticated admin session. is_admin is pinned against client writes
 * by profiles_privileged_guard (20260902090000), and the exemption is exactly
 * this: the service role sets it out of band. The admin RPCs read auth.uid(),
 * so they need the USER's session, not the service-role client.
 */
async function mintModerator(email: string, password: string) {
  const anonClient = createAnonClient();
  const { data, error } = await anonClient.auth.signUp({ email, password });
  if (error || !data.user || !data.session) {
    throw new Error(`signUp moderator: ${error?.message ?? "no session"}`);
  }
  const { error: promoteErr } = await admin
    .from("profiles").update({ is_admin: true }).eq("user_id", data.user.id);
  if (promoteErr) throw new Error(`promote moderator: ${promoteErr.message}`);
  return { id: data.user.id, client: anonClient, token: data.session.access_token };
}

async function invokeDeleteAccount(accessToken: string): Promise<Response> {
  return await fetch(`${SUPABASE_URL}/functions/v1/delete-account`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      apikey: ANON_KEY!,
      "Content-Type": "application/json",
    },
  });
}

const runId = crypto.randomUUID().slice(0, 8);
const password = `Purge-${crypto.randomUUID()}`;
const sellerEmail = `purge-test-seller-${runId}@example.test`;
const buyerEmail = `purge-test-buyer-${runId}@example.test`;

let sellerId: string | null = null;
let buyerId: string | null = null;
let moderatorId: string | null = null;

/** Last-resort cleanup so a failed run never leaves residue. */
async function forceCleanup(userId: string | null, label: string) {
  if (!userId) return;
  const { data } = await admin.auth.admin.getUserById(userId);
  if (!data?.user) return;
  console.log(`… force-cleaning ${label} via service role`);
  await admin.rpc("purge_account", { p_user_id: userId });
  for (const path of await listStorageFiles(userId).catch(() => [] as string[])) {
    await admin.storage.from("pebbles-media").remove([path]);
  }
  await admin.auth.admin.deleteUser(userId);
}

try {
  // -------------------------------------------------------------------------
  // 1. Create throwaway users (profiles arrive via handle_new_user).
  // -------------------------------------------------------------------------
  const { data: sellerUser, error: sellerErr } = await admin.auth.admin.createUser({
    email: sellerEmail,
    password,
    email_confirm: true,
    user_metadata: { full_name: `Purge Seller ${runId}` },
  });
  if (sellerErr || !sellerUser?.user) throw new Error(`createUser seller: ${sellerErr?.message}`);
  sellerId = sellerUser.user.id;

  const { data: buyerUser, error: buyerErr } = await admin.auth.admin.createUser({
    email: buyerEmail,
    password,
    email_confirm: true,
    user_metadata: { full_name: `Purge Buyer ${runId}` },
  });
  if (buyerErr || !buyerUser?.user) throw new Error(`createUser buyer: ${buyerErr?.message}`);
  buyerId = buyerUser.user.id;
  console.log(`Created seller ${sellerId} and buyer ${buyerId}`);

  // -------------------------------------------------------------------------
  // 2. Seed the seller with every entity type.
  // -------------------------------------------------------------------------
  const { data: systemGlyph } = await admin
    .from("glyphs").select("id").is("user_id", null).limit(1).single();
  const { data: emotion } = await admin.from("emotions").select("id").limit(1).single();
  const { data: cardType } = await admin.from("card_types").select("id").limit(1).single();
  const { data: domain } = await admin.from("domains").select("id").limit(1).single();
  if (!systemGlyph || !emotion || !cardType || !domain) {
    throw new Error("reference data missing (system glyph / emotion / card type / domain)");
  }

  const strokes = [{ points: [[20, 20], [180, 180]] }];
  const { data: soldGlyph, error: sgErr } = await admin
    .from("glyphs")
    .insert({ user_id: sellerId, name: `purge-test sold ${runId}`, strokes, view_box: "0 0 200 200" })
    .select("id").single();
  if (sgErr || !soldGlyph) throw new Error(`insert sold glyph: ${sgErr?.message}`);

  const { data: unsoldGlyph, error: ugErr } = await admin
    .from("glyphs")
    .insert({ user_id: sellerId, name: `purge-test unsold ${runId}`, strokes, view_box: "0 0 200 200" })
    .select("id").single();
  if (ugErr || !unsoldGlyph) throw new Error(`insert unsold glyph: ${ugErr?.message}`);

  // Approved + listed submission, seeded directly: approve_glyph is gated on
  // is_admin(auth.uid()), which a service-role client does not have.
  const { error: subErr } = await admin.from("glyph_submissions").insert({
    glyph_id: soldGlyph.id, submitter_id: sellerId, status: "approved", price: 25, listed: true,
  });
  if (subErr) throw new Error(`insert submission: ${subErr.message}`);

  const { data: soul, error: soulErr } = await admin
    .from("souls")
    .insert({ user_id: sellerId, name: `purge-test soul ${runId}`, glyph_id: systemGlyph.id })
    .select("id").single();
  if (soulErr || !soul) throw new Error(`insert soul: ${soulErr?.message}`);

  const { data: collection, error: colErr } = await admin
    .from("collections")
    .insert({ user_id: sellerId, name: `purge-test collection ${runId}` })
    .select("id").single();
  if (colErr || !collection) throw new Error(`insert collection: ${colErr?.message}`);

  // Storage objects under the seller prefix ({user_id}/{snap_id}/{file}.jpg).
  const snapId = crypto.randomUUID();
  const jpegByte = new Uint8Array([0xff, 0xd8, 0xff, 0xd9]);
  for (const file of ["original.jpg", "thumb.jpg"]) {
    const { error } = await admin.storage
      .from("pebbles-media")
      .upload(`${sellerId}/${snapId}/${file}`, jpegByte, { contentType: "image/jpeg" });
    if (error) throw new Error(`storage upload ${file}: ${error.message}`);
  }

  // The pebble goes through the real RPC as the signed-in seller so cards,
  // domain/soul/collection links, the snap row and the karma credit all take
  // the production path.
  const seller = createClient(SUPABASE_URL, ANON_KEY, { auth: { persistSession: false } });
  const { data: sellerSession, error: sellerSignIn } = await seller.auth.signInWithPassword({
    email: sellerEmail, password,
  });
  if (sellerSignIn || !sellerSession?.session) throw new Error(`seller sign-in: ${sellerSignIn?.message}`);

  const { data: pebbleId, error: pebErr } = await seller.rpc("create_pebble", {
    payload: {
      name: `purge-test pebble ${runId}`,
      description: "Seeded by verify-account-purge",
      happened_at: new Date().toISOString(),
      intensity: 2,
      positiveness: 1,
      visibility: "private",
      emotion_id: emotion.id,
      glyph_id: soldGlyph.id,
      soul_ids: [soul.id],
      collection_ids: [collection.id],
      domain_ids: [domain.id],
      cards: [{ species_id: cardType.id, value: "purge-test card", sort_order: 0 }],
      snaps: [{ id: snapId, storage_path: `${sellerId}/${snapId}`, sort_order: 0 }],
    },
  });
  if (pebErr || !pebbleId) throw new Error(`create_pebble: ${pebErr?.message}`);

  const { error: favSellerErr } = await admin
    .from("glyph_favourites").insert({ user_id: sellerId, glyph_id: soldGlyph.id });
  if (favSellerErr) throw new Error(`seller favourite: ${favSellerErr.message}`);

  // An unpublished draft (M47). Inserted as the signed-in seller so the
  // owner-only pebble_drafts_all policy is exercised, not bypassed.
  const { error: draftErr } = await seller.from("pebble_drafts").insert({
    user_id: sellerId,
    payload: { name: `purge-test draft ${runId}`, emotion_id: emotion.id },
  });
  if (draftErr) throw new Error(`insert draft: ${draftErr.message}`);

  // Connections rows (M49): a seller↔buyer connection, the seller's live
  // invite, and blocks in BOTH directions. Seeded via the service-role client:
  // the three tables have no client write policies (definer-RPC-only writes).
  const [connUserA, connUserB] =
    sellerId < buyerId ? [sellerId, buyerId] : [buyerId, sellerId];
  const { error: connErr } = await admin
    .from("connections").insert({ user_a: connUserA, user_b: connUserB });
  if (connErr) throw new Error(`insert connection: ${connErr.message}`);

  const { error: invErr } = await admin
    .from("connection_invites")
    .insert({ inviter_id: sellerId, token: `purge-test-token-${runId}` });
  if (invErr) throw new Error(`insert invite: ${invErr.message}`);

  for (const [blocker, blocked] of [[sellerId, buyerId], [buyerId, sellerId]]) {
    const { error: blockErr } = await admin
      .from("connection_blocks").insert({ blocker_id: blocker, blocked_id: blocked });
    if (blockErr) throw new Error(`insert block ${blocker}->${blocked}: ${blockErr.message}`);
  }

  // A public profile (M50). Claimed through the real RPC + direct toggle so
  // the purge run also proves the handle frees up (profiles-row delete).
  const handle = `purgetest${runId}`;
  const buyerHandle = `purgetestbuyer${runId}`;
  const { error: handleErr } = await seller.rpc("set_handle", { p_handle: handle });
  if (handleErr) throw new Error(`set_handle: ${handleErr.message}`);
  const { error: publicErr } = await seller
    .from("profiles").update({ public_profile: true }).eq("user_id", sellerId);
  if (publicErr) throw new Error(`public_profile toggle: ${publicErr.message}`);
  const { data: livePublic, error: livePublicErr } = await seller
    .rpc("get_public_profile", { p_handle: handle });
  if (livePublicErr || !livePublic) {
    throw new Error(`get_public_profile pre-purge: ${livePublicErr?.message ?? "null"}`);
  }

  // Art. 9 consent ledger rows (#775). Written through the real RPC as the
  // signed-in seller — user_consents has no client insert policy at all, so a
  // direct admin insert would prove nothing about the path the app uses.
  const CONSENT_DOC_VERSION = "1.1.0";
  const { error: consentErr } = await seller.rpc("record_consent", {
    p_kind: "health_data",
    p_document_version: CONSENT_DOC_VERSION,
    p_source: "web_register",
  });
  if (consentErr) throw new Error(`record_consent health_data: ${consentErr.message}`);

  const { error: publicConsentErr } = await seller.rpc("record_consent", {
    p_kind: "public_profile",
    p_document_version: CONSENT_DOC_VERSION,
    p_source: "web_settings",
  });
  if (publicConsentErr) throw new Error(`record_consent public_profile: ${publicConsentErr.message}`);

  const { error: ageConsentErr } = await seller.rpc("record_consent", {
    p_kind: "age_assurance",
    p_document_version: CONSENT_DOC_VERSION,
    p_source: "web_register",
  });
  if (ageConsentErr) throw new Error(`record_consent age_assurance: ${ageConsentErr.message}`);

  // The age attestation must not be withdrawable: you cannot un-attest your
  // age, and a withdrawn row would be indistinguishable from an account that
  // never attested. Two things enforce that, and this block pins both:
  // withdraw_consent's p_kind allowlist (20260911090060_user_consents_hardening
  // .sql §4) deliberately omits age_assurance, and the
  // user_consents_age_not_withdrawable CHECK backs it structurally. If either
  // is widened "for symmetry", this fails.
  //
  // Match the message, not merely "an error happened": a renamed/re-arity'd RPC
  // (PGRST202), a dropped grant (42501), an expired session or a silently
  // no-op'd seed (no_active_consent) all error too, and those are exactly the
  // states a careless refactor produces. Only `invalid_kind` proves the
  // allowlist did the refusing.
  const { error: withdrawAgeErr } = await seller.rpc("withdraw_consent", {
    p_kind: "age_assurance",
  });
  check("withdraw_consent refuses age_assurance with invalid_kind",
    withdrawAgeErr?.message?.includes("invalid_kind") === true,
    `expected invalid_kind, got: ${withdrawAgeErr?.message ?? "no error at all"}` +
      ` (code=${withdrawAgeErr?.code ?? "none"})`);

  // …and the refused call left the attestation ACTIVE. The consentCount check
  // below counts rows whatever their state, so a withdrawal that succeeded
  // would still total 3; only the withdrawn_at/superseded_at predicates tell
  // the two apart. Read through `admin` like every other assertion here, so
  // the result is ground truth rather than a function of RLS.
  const { count: activeAge, error: activeAgeErr } = await admin
    .from("user_consents")
    .select("*", { count: "exact", head: true })
    .eq("user_id", sellerId)
    .eq("kind", "age_assurance")
    .is("withdrawn_at", null)
    .is("superseded_at", null);
  if (activeAgeErr) throw new Error(`count active age_assurance: ${activeAgeErr.message}`);
  check("age attestation still active after the refused withdrawal", activeAge === 1,
    `found ${activeAge ?? 0} active age_assurance rows, expected 1`);

  const consentCount = await countRows("user_consents", "user_id", sellerId);
  if (consentCount !== 3) {
    throw new Error(`expected 3 seeded consent rows, got ${consentCount}`);
  }

  // Achievement unlocks (M48). Earned through the real RPC as the signed-in
  // seller (achievement_unlocks has no client insert policy): the pebble,
  // soul, collection and glyphs above qualify several badges in one call.
  const { data: unlockRows, error: unlockErr } = await seller.rpc("check_achievements");
  if (unlockErr) throw new Error(`check_achievements: ${unlockErr.message}`);
  if (!Array.isArray(unlockRows) || unlockRows.length === 0) {
    throw new Error("check_achievements unlocked nothing — seed should qualify several badges");
  }

  // -------------------------------------------------------------------------
  // 3. The sale: fund the buyer, buy through the real RPC, favourite.
  // -------------------------------------------------------------------------
  const { error: grantErr } = await admin.from("karma_events").insert({
    user_id: buyerId, delta: 100, type: "credit", reason: "grant",
  });
  if (grantErr) throw new Error(`grant karma: ${grantErr.message}`);

  const buyer = createClient(SUPABASE_URL, ANON_KEY, { auth: { persistSession: false } });
  const { data: buyerSession, error: buyerSignIn } = await buyer.auth.signInWithPassword({
    email: buyerEmail, password,
  });
  if (buyerSignIn || !buyerSession?.session) throw new Error(`buyer sign-in: ${buyerSignIn?.message}`);

  const { error: buyErr } = await buyer.rpc("buy_glyph", { p_glyph_id: soldGlyph.id });
  if (buyErr) throw new Error(`buy_glyph: ${buyErr.message}`);

  const { error: favBuyerErr } = await buyer
    .from("glyph_favourites").insert({ user_id: buyerId, glyph_id: soldGlyph.id });
  if (favBuyerErr) throw new Error(`buyer favourite: ${favBuyerErr.message}`);

  console.log(`Seeded: pebble ${pebbleId}, sold glyph ${soldGlyph.id}, unsold glyph ${unsoldGlyph.id}\n`);

  // ---------------------------------------------------------------------------
  // content_reports (#831). Four seeds, covering both purge directions and the
  // two things the anon-only harness cannot reach.
  // ---------------------------------------------------------------------------

  // (a) The buyer reports the seller's PUBLIC pebble. This is the row that
  //     must be DELETED when the seller goes.
  const { data: pubPebbleId, error: pubPebbleErr } = await seller.rpc("create_pebble", {
    payload: {
      happened_at: "2026-09-01T12:00:00Z",
      intensity: 2,
      positiveness: 1,
      emotion_id: emotion.id,
      name: "reportable public",
      description: "original text",
      visibility: "public",
    },
  });
  if (pubPebbleErr || !pubPebbleId) throw new Error(`public pebble: ${pubPebbleErr?.message}`);

  const { data: pebbleReport, error: pebbleReportErr } = await buyer.rpc("report_content", {
    p_target_kind: "pebble",
    p_target_id: pubPebbleId,
    p_reason: "harassment",
    p_detail: "purge harness",
  });
  check("buyer can report the seller's public pebble",
    !pebbleReportErr && !!(pebbleReport as { id?: string } | null)?.id, pebbleReportErr?.message);

  // The correctness assertion the anon harness cannot make: the owner was
  // resolved server-side, and the snapshot captured the text at file time.
  const { data: storedReport } = await admin
    .from("content_reports").select("target_user_id, target_snapshot, status")
    .eq("id", (pebbleReport as { id: string }).id).maybeSingle();
  check("report resolved target_user_id server-side",
    storedReport?.target_user_id === sellerId,
    `got ${storedReport?.target_user_id}, want ${sellerId}`);
  check("report snapshotted the reported text",
    (storedReport?.target_snapshot as { description?: string } | null)?.description === "original text",
    JSON.stringify(storedReport?.target_snapshot));

  // Editing after the fact must NOT rewrite the evidence.
  // update_pebble takes the pebble id as its own arg (p_pebble_id), separate
  // from the payload jsonb (packages/supabase/types/database.ts) — the plan's
  // draft nested `id` inside `payload`, which the RPC never reads, so the edit
  // would silently no-op and both snapshot checks below would pass vacuously.
  const { error: updateErr } = await seller.rpc("update_pebble", {
    p_pebble_id: pubPebbleId,
    payload: { description: "innocent now" },
  });
  if (updateErr) throw new Error(`update_pebble: ${updateErr.message}`);
  const { data: afterEdit } = await admin
    .from("content_reports").select("target_snapshot")
    .eq("id", (pebbleReport as { id: string }).id).maybeSingle();
  check("editing the pebble does not rewrite the snapshot",
    (afterEdit?.target_snapshot as { description?: string } | null)?.description === "original text",
    JSON.stringify(afterEdit?.target_snapshot));

  // (b) A LISTED glyph is reportable — the case the anon harness cannot reach,
  //     because approving a submission needs an admin.
  const { data: glyphReport, error: glyphReportErr } = await buyer.rpc("report_content", {
    p_target_kind: "glyph",
    p_target_id: soldGlyph.id,
    p_reason: "sexual",
  });
  check("a listed marketplace glyph is reportable",
    !glyphReportErr && !!(glyphReport as { id?: string } | null)?.id, glyphReportErr?.message);

  // (c) The seller reports the BUYER. This is the row that must SURVIVE the
  //     seller's purge with reporter_id detached.
  const { error: buyerHandleErr } = await buyer.rpc("set_handle", { p_handle: buyerHandle });
  if (buyerHandleErr) throw new Error(`buyer set_handle: ${buyerHandleErr.message}`);
  const { error: buyerPubErr } = await admin
    .from("profiles").update({ public_profile: true }).eq("user_id", buyerId);
  if (buyerPubErr) throw new Error(`buyer publish: ${buyerPubErr.message}`);

  const { data: sellerFiled, error: sellerFiledErr } = await seller.rpc("report_content", {
    p_target_kind: "profile",
    p_target_id: buyerId,
    p_reason: "impersonation",
  });
  check("seller can report the buyer's public profile",
    !sellerFiledErr && !!(sellerFiled as { id?: string } | null)?.id, sellerFiledErr?.message);
  const sellerFiledId = (sellerFiled as { id: string }).id;

  // ---------------------------------------------------------------------------
  // The POSITIVE admin path — queue read and takedown dispatch. Needs a real
  // admin session, which only the service role can mint.
  // ---------------------------------------------------------------------------
  const moderator = await mintModerator(
    `purge-test-mod-${runId}@example.test`,
    password,
  );
  moderatorId = moderator.id;

  const { data: queue, error: queueErr } = await moderator.client
    .rpc("admin_list_content_reports", { p_status: "open" });
  const queueRows = (queue ?? []) as Array<Record<string, unknown>>;
  check("admin queue returns the open reports", !queueErr && queueRows.length >= 3,
    queueErr ? queueErr.message : `rows=${queueRows.length}`);
  const queuedPebble = queueRows.find((r) => r.id === (pebbleReport as { id: string }).id);
  check("queue shows live content beside the snapshot",
    (queuedPebble?.target_live as { description?: string } | null)?.description === "innocent now",
    JSON.stringify(queuedPebble?.target_live));
  check("queue counts open reports per target",
    Number(queuedPebble?.open_reports_against_target) === 1,
    String(queuedPebble?.open_reports_against_target));
  check("queue does not leak reporter emails",
    !Object.keys(queuedPebble ?? {}).some((k) => k.includes("email")),
    Object.keys(queuedPebble ?? {}).join(","));

  // Takedown, one per target_kind.
  const { error: takePebbleErr } = await moderator.client.rpc("resolve_content_report", {
    p_report_id: (pebbleReport as { id: string }).id,
    p_outcome: "actioned",
    p_note: "harness takedown",
  });
  const { data: takenPebble } = await admin
    .from("pebbles").select("visibility, hidden_at").eq("id", pubPebbleId).maybeSingle();
  // #833 replaced the destructive takedown: actioning now sets hidden_at and
  // leaves the user's own visibility setting alone. Both halves are asserted —
  // "it is hidden" without "and visibility was not overwritten" would pass just
  // as well against the old destructive behaviour this change exists to remove.
  check("actioning a pebble report hides it",
    !takePebbleErr && takenPebble?.hidden_at !== null,
    takePebbleErr ? takePebbleErr.message : `hidden_at=${takenPebble?.hidden_at}`);
  check("actioning leaves the owner's visibility setting untouched",
    takenPebble?.visibility === "public",
    `visibility=${takenPebble?.visibility}`);

  const { error: takeGlyphErr } = await moderator.client.rpc("resolve_content_report", {
    p_report_id: (glyphReport as { id: string }).id,
    p_outcome: "actioned",
  });
  const { data: takenSub } = await admin
    .from("glyph_submissions").select("listed").eq("glyph_id", soldGlyph.id).maybeSingle();
  check("actioning a glyph report delists it",
    !takeGlyphErr && takenSub?.listed === false,
    takeGlyphErr ? takeGlyphErr.message : String(takenSub?.listed));

  // Resolving twice is refused — the verdict is not re-writable.
  const { error: reResolveErr } = await moderator.client.rpc("resolve_content_report", {
    p_report_id: (pebbleReport as { id: string }).id, p_outcome: "dismissed",
  });
  check("a resolved report cannot be resolved again",
    !!reResolveErr && reResolveErr.message.includes("invalid_state"), reResolveErr?.message);

  // -------------------------------------------------------------------------
  // 4. Delete the seller through the real edge function.
  // -------------------------------------------------------------------------
  const res = await invokeDeleteAccount(sellerSession.session.access_token);
  const body = await res.json().catch(() => ({}));
  check("delete-account responds 200 { ok: true }", res.status === 200 && body?.ok === true,
    `status=${res.status} body=${JSON.stringify(body)}`);
  if (body?.purged) console.log(`  purge counts: ${JSON.stringify(body.purged)}`);

  // Assert the purge's OWN accounting for the section-(4) tables: they all
  // cascade from auth.users, so by the time the zero-row checks below run
  // (after deleteUser) the cascade would empty them even if a purge delete
  // line were dropped. Only the RPC's counts can catch that, so pin the
  // M47/M49 rows to what §2 seeded.
  const purgedCounts = (body?.purged?.counts ?? {}) as Record<string, number>;
  const expectedPurged: Array<[string, number]> = [
    ["pebble_drafts", 1], // the M47 draft
    ["connections", 1], // the seller↔buyer row
    ["connection_invites", 1], // the seller's live invite
    ["connection_blocks", 2], // both directions
    ["user_consents", 3], // health_data + public_profile + age_assurance
  ];
  for (const [key, expected] of expectedPurged) {
    check(`purge itself counted ${key} = ${expected}`, purgedCounts[key] === expected,
      `got ${purgedCounts[key] ?? "missing"}`);
  }

  // -------------------------------------------------------------------------
  // 5. Assertions.
  // -------------------------------------------------------------------------
  const sellerScoped: Array<[string, string]> = [
    ["profiles", "user_id"],
    ["souls", "user_id"],
    ["glyphs", "user_id"],
    ["pebbles", "user_id"],
    ["collections", "user_id"],
    ["snaps", "user_id"],
    ["karma_events", "user_id"],
    ["glyph_entitlements", "user_id"],
    ["glyph_favourites", "user_id"],
    ["glyph_submissions", "submitter_id"],
    ["glyph_submissions", "reviewed_by"],
    ["wallet_balances", "user_id"],
    ["bounces", "user_id"],
    ["log_reactions", "user_id"],
    ["pebble_drafts", "user_id"],
    ["connection_invites", "inviter_id"],
    ["achievement_unlocks", "user_id"],
    ["user_consents", "user_id"],
    ["content_reports", "target_user_id"],
    ["content_reports", "reporter_id"],
  ];
  for (const [table, column] of sellerScoped) {
    const n = await countRows(table, column, sellerId);
    check(`no seller rows in ${table}.${column}`, n === 0, `found ${n}`);
  }

  // connections and connection_blocks are TWO-SIDED (user_a/user_b,
  // blocker_id/blocked_id — not user_id-shaped), so the single-column loop
  // above cannot cover them: count each side explicitly.
  for (const [table, column] of [
    ["connections", "user_a"],
    ["connections", "user_b"],
    ["connection_blocks", "blocker_id"],
    ["connection_blocks", "blocked_id"],
  ]) {
    const n = await countRows(table, column, sellerId);
    check(`no seller rows in ${table}.${column}`, n === 0, `found ${n}`);
  }
  // Deleting A removes blocks in BOTH directions, so the buyer's own
  // buyer→seller block row is gone too (blocked side purged).
  const buyerBlocks = await countRows("connection_blocks", "blocker_id", buyerId);
  check("buyer's block row purged with the seller", buyerBlocks === 0, `found ${buyerBlocks}`);
  for (const table of ["pebble_cards", "pebble_souls", "pebble_domains", "collection_pebbles"]) {
    const n = await countRows(table, "pebble_id", pebbleId as string);
    check(`pebble cascade emptied ${table}`, n === 0, `found ${n}`);
  }

  const { data: keptGlyph } = await admin
    .from("glyphs").select("user_id, strokes, name").eq("id", soldGlyph.id).maybeSingle();
  check("sold glyph still exists", !!keptGlyph);
  check("sold glyph is anonymized (user_id null)", keptGlyph?.user_id === null);
  check("sold glyph strokes intact (buyer's glyph still renders)",
    Array.isArray(keptGlyph?.strokes) && keptGlyph.strokes.length === strokes.length);

  const { data: keptSub } = await admin
    .from("glyph_submissions").select("status, listed, submitter_id")
    .eq("glyph_id", soldGlyph.id).maybeSingle();
  check("kept submission still approved (audit trail)", keptSub?.status === "approved");
  check("kept submission delisted", keptSub?.listed === false);
  check("kept submission detached from seller", keptSub?.submitter_id === null);

  const buyerEntitlements = await countRows("glyph_entitlements", "user_id", buyerId);
  check("buyer entitlement survives", buyerEntitlements === 1, `found ${buyerEntitlements}`);
  const buyerFavs = await countRows("glyph_favourites", "user_id", buyerId);
  check("buyer favourite survives", buyerFavs === 1, `found ${buyerFavs}`);

  const unsoldCount = await countRows("glyphs", "id", unsoldGlyph.id);
  check("unsold glyph deleted", unsoldCount === 0, `found ${unsoldCount}`);

  const leftover = await listStorageFiles(sellerId);
  check("storage prefix empty", leftover.length === 0, `found ${leftover.join(", ")}`);

  // M50: the profiles-row delete freed the handle — the public projection
  // resolves null (indistinguishable from never-existed).
  const { data: freedHandle, error: freedHandleErr } = await admin
    .rpc("get_public_profile", { p_handle: handle });
  check("purged handle resolves null via get_public_profile",
    !freedHandleErr && freedHandle === null,
    freedHandleErr ? freedHandleErr.message : JSON.stringify(freedHandle));

  // ...but the report the seller FILED survives, detached. The moderation
  // trail must outlive the reporter.
  const { data: survivor } = await admin
    .from("content_reports").select("id, reporter_id, target_user_id")
    .eq("id", sellerFiledId).maybeSingle();
  check("the report the seller filed survives the purge", !!survivor,
    "the moderation trail was destroyed with the reporter");
  check("the surviving report is detached from the seller",
    survivor?.reporter_id === null, String(survivor?.reporter_id));
  check("the surviving report still names its target",
    survivor?.target_user_id === buyerId, String(survivor?.target_user_id));

  const { data: goneUser } = await admin.auth.admin.getUserById(sellerId);
  check("auth user gone", !goneUser?.user);

  const { data: rerun, error: rerunErr } = await admin.rpc("purge_account", { p_user_id: sellerId });
  const rerunCounts = (rerun as { counts?: Record<string, number>; kept_glyphs?: number }) ?? {};
  const allZero = Object.values(rerunCounts.counts ?? { x: 1 }).every((n) => n === 0);
  check("purge_account re-run converges (no error, all-zero counts)",
    !rerunErr && allZero && rerunCounts.kept_glyphs === 0,
    rerunErr ? rerunErr.message : JSON.stringify(rerun));

  // -------------------------------------------------------------------------
  // 6. Cleanup: the buyer deletes their own account through the same path
  //    (exercises own-entitlement-before-karma_events on the buyer side).
  // -------------------------------------------------------------------------
  const buyerRes = await invokeDeleteAccount(buyerSession.session.access_token);
  const buyerBody = await buyerRes.json().catch(() => ({}));
  check("buyer delete-account responds 200 { ok: true }",
    buyerRes.status === 200 && buyerBody?.ok === true,
    `status=${buyerRes.status} body=${JSON.stringify(buyerBody)}`);
  const { data: goneBuyer } = await admin.auth.admin.getUserById(buyerId);
  check("buyer auth user gone", !goneBuyer?.user);
} catch (err) {
  failed += 1;
  console.error(`✗ aborted: ${err instanceof Error ? err.message : String(err)}`);
} finally {
  try {
    await forceCleanup(sellerId, "seller");
    await forceCleanup(buyerId, "buyer");
    if (moderatorId) await forceCleanup(moderatorId, "moderator");
  } catch (err) {
    console.error(`cleanup failed — remove purge-test-* users manually: ${err}`);
  }
}

console.log(`\nSummary: passed=${passed} failed=${failed}`);
Deno.exit(failed > 0 ? 1 : 0);
