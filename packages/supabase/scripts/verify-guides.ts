#!/usr/bin/env -S deno run --allow-env --allow-net
/**
 * Acceptance test for the guides table (#1254) — runs against the REMOTE project.
 *
 *   1. anon and a signed-in user read the published guides, ordered by
 *      sort_order, and the nine seeded slugs are all there.
 *   2. a signed-in user cannot insert, update or delete a guide (admin-only
 *      writes). The insert reports an error AND no row appears; the update and
 *      delete are refused as 0 rows (what a `for update/delete using (...)`
 *      policy returns over PostgREST), so each re-reads the stored row through
 *      anon and asserts it unchanged. The re-read is the proof: a 0-row filter
 *      alone would prove nothing.
 *   3. the FR fallback contract: every seeded row has title_fr and summary_fr,
 *      and body_md_fr may be null (clients fall back to EN).
 *
 * Run: set -a; . ./.env; set +a; npm run db:verify:guides --workspace=packages/supabase
 * Expected: `Summary: passed=8 failed=0`
 * Needs NO service-role key: it signs up one throwaway user (guides-verify-*@example.test)
 * and deletes it through the real `delete-account` edge function, even on failure.
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
  if (ok) { passed += 1; console.log(`✓ ${name}`); }
  else { failed += 1; console.log(`✗ ${name}${detail ? ` — ${detail}` : ""}`); }
}

const SEEDED = ["pebbles", "stones", "emotions", "valence", "domains", "collections", "souls", "glyphs", "karma"];

type Guide = {
  id: string; slug: string; sort_order: number; illustration: string | null;
  title_en: string; title_fr: string | null; summary_en: string; summary_fr: string | null;
  body_md_en: string; body_md_fr: string | null; published: boolean;
};

const runId = crypto.randomUUID().slice(0, 8);
const password = `Guides-${crypto.randomUUID()}`;
type TestUser = { client: SupabaseClient; id: string; token: string };

async function signUp(label: string): Promise<TestUser> {
  const client = createClient(SUPABASE_URL!, ANON_KEY!, { auth: { persistSession: false } });
  const email = `guides-verify-${label}-${runId}@example.test`;
  const { data, error } = await client.auth.signUp({ email, password });
  if (error || !data.session || !data.user) {
    throw new Error(`signUp ${label}: ${error?.message ?? "no session (email confirmations on?)"}`);
  }
  return { client, id: data.user.id, token: data.session.access_token };
}

async function deleteAccount(token: string): Promise<Response> {
  return await fetch(`${SUPABASE_URL}/functions/v1/delete-account`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, apikey: ANON_KEY!, "Content-Type": "application/json" },
  });
}

const anon = createClient(SUPABASE_URL, ANON_KEY, { auth: { persistSession: false } });
let user: TestUser | null = null;

try {
  // 1. reads --------------------------------------------------------------------------------
  const anonRead = await anon.from("guides").select("*").order("sort_order");
  const anonRows = (anonRead.data ?? []) as Guide[];
  check("anon reads the published guides in sort_order",
    !anonRead.error && anonRows.length >= SEEDED.length
      && anonRows.every((g, i) => i === 0 || g.sort_order >= anonRows[i - 1].sort_order),
    anonRead.error?.message ?? `${anonRows.length} rows`);
  check("the nine seeded slugs are published",
    SEEDED.every((s) => anonRows.some((g) => g.slug === s)),
    SEEDED.filter((s) => !anonRows.some((g) => g.slug === s)).join(", "));
  check("every seeded row carries title_fr and summary_fr (FR falls back to EN only for the body)",
    anonRows.filter((g) => SEEDED.includes(g.slug)).every((g) => g.title_fr && g.summary_fr && g.body_md_en),
    JSON.stringify(anonRows.filter((g) => SEEDED.includes(g.slug) && !(g.title_fr && g.summary_fr)).map((g) => g.slug)));

  user = await signUp("a");
  const u = user.client;
  const userRead = await u.from("guides").select("slug").order("sort_order");
  check("a signed-in user reads the same published set",
    !userRead.error && (userRead.data ?? []).length === anonRows.length,
    userRead.error?.message ?? `${(userRead.data ?? []).length} vs ${anonRows.length}`);

  // 2. writes are admin-only ----------------------------------------------------------------
  const { error: insErr } = await u.from("guides").insert({
    slug: `verify-${runId}`, title_en: "x", summary_en: "x", body_md_en: "x", published: true,
  });
  const { data: inserted } = await anon.from("guides").select("id").eq("slug", `verify-${runId}`);
  check("a user cannot insert a guide, and no row appears",
    insErr !== null && (inserted ?? []).length === 0,
    insErr?.message ?? "the insert was accepted");

  const target = anonRows.find((g) => g.slug === "karma")!;
  const { error: updErr } = await u.from("guides").update({ title_en: `hacked ${runId}` }).eq("id", target.id);
  const { data: after } = await anon.from("guides").select("title_en").eq("id", target.id).maybeSingle();
  check("a user cannot update a guide (error or 0 rows), and the stored title is unchanged",
    after?.title_en === target.title_en,
    `${updErr?.message ?? "no error"}; title now ${after?.title_en}`);

  const { error: delErr } = await u.from("guides").delete().eq("id", target.id);
  const { data: still } = await anon.from("guides").select("id").eq("id", target.id).maybeSingle();
  check("a user cannot delete a guide, and the row is still there",
    still?.id === target.id,
    `${delErr?.message ?? "no error"}; row ${still ? "present" : "gone"}`);

  // 3. unpublished rows are invisible to anon (an admin draft stays a draft) ---------------
  const { data: drafts } = await anon.from("guides").select("id").eq("published", false);
  check("anon sees no unpublished guide", (drafts ?? []).length === 0, `${(drafts ?? []).length} drafts visible`);
} catch (err) {
  failed += 1;
  console.error(`✗ aborted: ${err instanceof Error ? err.message : String(err)}`);
} finally {
  if (user) {
    const res = await deleteAccount(user.token).catch(() => null);
    if (!res?.ok) console.error(`cleanup: delete-account returned ${res?.status ?? "no response"}`);
  }
}

console.log(`\nSummary: passed=${passed} failed=${failed}`);
Deno.exit(failed > 0 ? 1 : 0);
