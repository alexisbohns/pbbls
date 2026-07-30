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

  // A public profile (M50). Claimed through the real RPC + direct toggle so
  // the purge run also proves the handle frees up (profiles-row delete).
  const handle = `purgetest${runId}`;
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

  // -------------------------------------------------------------------------
  // 4. Delete the seller through the real edge function.
  // -------------------------------------------------------------------------
  const res = await invokeDeleteAccount(sellerSession.session.access_token);
  const body = await res.json().catch(() => ({}));
  check("delete-account responds 200 { ok: true }", res.status === 200 && body?.ok === true,
    `status=${res.status} body=${JSON.stringify(body)}`);
  if (body?.purged) console.log(`  purge counts: ${JSON.stringify(body.purged)}`);

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
    ["achievement_unlocks", "user_id"],
  ];
  for (const [table, column] of sellerScoped) {
    const n = await countRows(table, column, sellerId);
    check(`no seller rows in ${table}.${column}`, n === 0, `found ${n}`);
  }
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
  } catch (err) {
    console.error(`cleanup failed — remove purge-test-* users manually: ${err}`);
  }
}

console.log(`\nSummary: passed=${passed} failed=${failed}`);
Deno.exit(failed > 0 ? 1 : 0);
