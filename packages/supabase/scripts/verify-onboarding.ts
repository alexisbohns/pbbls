#!/usr/bin/env -S deno run --allow-env --allow-net --allow-read --allow-write
/**
 * Acceptance test for the interactive onboarding contract (#1510 §2, §4) — runs against the REMOTE project, anon only.
 *   1. promise_letters: anon reads the published letter (EN body, FR body, signature); anon cannot write.
 *   2. complete_onboarding happy path: name, glyph, three reasons, a pace and three souls land in one call;
 *      onboarding_completed turns true; the souls are ordinary rows with the glyph given.
 *   3. Idempotent: a second call with other values changes nothing (no fourth soul, same name).
 *   4. Refusals on a fresh user, each proven by a re-read that shows nothing landed:
 *      a fourth soul, a soul without a glyph, another user's glyph, a duplicate name, an unknown reason,
 *      an unknown pace, a non-array souls payload. Then everything skipped still completes.
 *   5. Anon is refused. The two profile columns are not in get_public_profile.
 *   6. ONBOARDING_DUMP=1 writes the verbatim payload sent in 2 to scripts/oracles/onboarding/payloads/complete_onboarding.json,
 *      which the clients' foreign-payload tests replay byte for byte.
 * Run: set -a; . ./.env; set +a; npm run db:verify:onboarding --workspace=packages/supabase
 * Expected: `Summary: passed=25 failed=0`
 * Signs up throwaway users (onboarding-verify-*@example.test) and deletes them through the real
 * `delete-account` edge function, even on failure.
 */
import { createClient, type SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL");
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY");
if (!SUPABASE_URL || !ANON_KEY) { console.error("SUPABASE_URL and SUPABASE_ANON_KEY must be set"); Deno.exit(2); }

let passed = 0; let failed = 0;
function check(name: string, ok: boolean, detail?: string) {
  if (ok) { passed += 1; console.log(`✓ ${name}`); } else { failed += 1; console.log(`✗ ${name}${detail ? ` — ${detail}` : ""}`); }
}

const runId = crypto.randomUUID().slice(0, 8);
const password = `Onb-${crypto.randomUUID()}`;
type TestUser = { client: SupabaseClient; id: string; token: string };

async function signUp(label: string): Promise<TestUser> {
  const client = createClient(SUPABASE_URL!, ANON_KEY!, { auth: { persistSession: false } });
  const email = `onboarding-verify-${label}-${runId}@example.test`;
  const { data, error } = await client.auth.signUp({ email, password });
  if (error || !data.session || !data.user) throw new Error(`signUp ${label}: ${error?.message ?? "no session"}`);
  return { client, id: data.user.id, token: data.session.access_token };
}
async function deleteAccount(token: string): Promise<Response> {
  return await fetch(`${SUPABASE_URL}/functions/v1/delete-account`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, apikey: ANON_KEY!, "Content-Type": "application/json" },
  });
}

type Profile = { display_name: string; glyph_id: string | null; onboarding_reasons: string[] | null; pace: string | null; onboarding_completed: boolean };
async function profile(u: TestUser): Promise<Profile> {
  const { data, error } = await u.client.from("profiles").select("display_name, glyph_id, onboarding_reasons, pace, onboarding_completed").single();
  if (error) throw new Error(`profile: ${error.message}`);
  return data as Profile;
}
async function souls(u: TestUser): Promise<{ name: string; glyph_id: string }[]> {
  const { data, error } = await u.client.from("souls").select("name, glyph_id").order("name");
  if (error) throw new Error(`souls: ${error.message}`);
  return data as { name: string; glyph_id: string }[];
}
async function systemGlyphs(u: TestUser): Promise<string[]> {
  const { data, error } = await u.client.from("glyphs").select("id").eq("is_system", true).order("created_at").limit(3);
  if (error) throw new Error(`glyphs: ${error.message}`);
  return (data ?? []).map((g) => g.id as string);
}
/** A refused call must leave the profile incomplete and soulless. */
async function refused(u: TestUser, name: string, args: Record<string, unknown>, expect: string) {
  const { error } = await u.client.rpc("complete_onboarding", args);
  const p = await profile(u);
  const s = await souls(u);
  check(`${name} is refused (${expect})`, error !== null && error.message.includes(expect) && !p.onboarding_completed && s.length === 0,
    error?.message ?? "no error");
}

const DUMP = Deno.env.get("ONBOARDING_DUMP") === "1";
const anon = createClient(SUPABASE_URL, ANON_KEY, { auth: { persistSession: false } });
let alice: TestUser | undefined; let bob: TestUser | undefined; let carol: TestUser | undefined;

try {
  // 1. promise_letters
  const letters = await anon.from("promise_letters").select("body_md_en, body_md_fr, signed_by_en, signed_by_fr, updated_at").order("updated_at", { ascending: false });
  const letter = letters.data?.[0] as { body_md_en: string; body_md_fr: string | null; signed_by_en: string } | undefined;
  check("anon reads a published promise letter", !letters.error && !!letter && letter.body_md_en.includes("never be a feed"), letters.error?.message ?? letter?.body_md_en);
  check("the letter is signed", letter?.signed_by_en === "The Pebbles team", letter?.signed_by_en);
  const ins = await anon.from("promise_letters").insert({ body_md_en: `verify-${runId}`, published: true });
  const after = await anon.from("promise_letters").select("id").like("body_md_en", "verify-%");
  check("anon insert is refused", ins.error !== null && (after.data ?? []).length === 0, ins.error?.message);
  await anon.from("promise_letters").update({ body_md_en: "hijacked" }).eq("published", true);
  const still = await anon.from("promise_letters").select("body_md_en").order("updated_at", { ascending: false });
  check("anon update is refused", still.data?.[0]?.body_md_en === letter?.body_md_en);

  // 2. happy path
  alice = await signUp("a");
  bob = await signUp("b");
  carol = await signUp("c");
  const [g1, g2, g3] = await systemGlyphs(alice);
  check("three system glyphs exist for the starter grid", !!g1 && !!g2 && !!g3);
  const payload = {
    p_display_name: "  Maya  ",
    p_glyph_id: g1,
    p_reasons: ["notice-feelings", "keep-people", "keep-record"],
    p_pace: "weekly-wrap",
    p_souls: [{ name: "Noé", glyph_id: g1 }, { name: " Léa ", glyph_id: g2 }, { name: "Jo", glyph_id: g3 }],
  };
  if (DUMP) {
    await Deno.mkdir(new URL("./oracles/onboarding/payloads/", import.meta.url), { recursive: true });
    await Deno.writeTextFile(new URL("./oracles/onboarding/payloads/complete_onboarding.json", import.meta.url), JSON.stringify(payload, null, 2) + "\n");
  }
  const happy = await alice.client.rpc("complete_onboarding", payload);
  check("complete_onboarding succeeds", happy.error === null, happy.error?.message);
  const pa = await profile(alice);
  check("display name is trimmed and the glyph set", pa.display_name === "Maya" && pa.glyph_id === g1, JSON.stringify(pa));
  check("reasons and pace land", JSON.stringify(pa.onboarding_reasons) === JSON.stringify(payload.p_reasons) && pa.pace === "weekly-wrap", JSON.stringify(pa));
  check("onboarding_completed is true", pa.onboarding_completed);
  const sa = await souls(alice);
  check("three ordinary souls with their glyphs, names trimmed",
    sa.length === 3 && sa.map((s) => s.name).sort().join(",") === "Jo,Léa,Noé" && sa.find((s) => s.name === "Léa")?.glyph_id === g2, JSON.stringify(sa));

  // 3. idempotent
  const again = await alice.client.rpc("complete_onboarding", { p_display_name: "Other", p_souls: [{ name: "Zed", glyph_id: g1 }] });
  const pa2 = await profile(alice);
  check("a second call is a no-op", again.error === null && pa2.display_name === "Maya" && (await souls(alice)).length === 3, again.error?.message);

  // 4. refusals (bob stays fresh between them: each refusal rolls back)
  const { data: carolGlyph, error: carveError } = await carol.client.from("glyphs")
    .insert({ user_id: carol.id, strokes: [[{ x: 10, y: 10 }, { x: 190, y: 190 }]], view_box: "0 0 200 200", name: `verify-${runId}` })
    .select("id").single();
  check("carol carves a glyph of her own", carveError === null && !!carolGlyph?.id, carveError?.message);
  const foreign = carolGlyph?.id ?? g1;
  await refused(bob, "a fourth soul", { p_souls: [{ name: "A", glyph_id: g1 }, { name: "B", glyph_id: g1 }, { name: "C", glyph_id: g1 }, { name: "D", glyph_id: g1 }] }, "too_many_souls");
  await refused(bob, "a soul without a glyph", { p_souls: [{ name: "A", glyph_id: null }] }, "invalid_soul_glyph");
  await refused(bob, "another user's glyph on a soul", { p_souls: [{ name: "A", glyph_id: foreign }] }, "glyph_not_usable");
  await refused(bob, "another user's glyph on the profile", { p_glyph_id: foreign }, "glyph_not_usable");
  await refused(bob, "a duplicate name", { p_souls: [{ name: "Ana", glyph_id: g1 }, { name: " ana ", glyph_id: g2 }] }, "duplicate_soul");
  await refused(bob, "a blank name", { p_souls: [{ name: "   ", glyph_id: g1 }] }, "invalid_soul_name");
  await refused(bob, "an unknown reason", { p_reasons: ["notice-feelings", "get-rich"] }, "invalid_reason");
  await refused(bob, "an empty reasons array", { p_reasons: [] }, "invalid_reason");
  await refused(bob, "an unknown pace", { p_pace: "hourly" }, "invalid_pace");
  await refused(bob, "a non-array souls payload", { p_souls: { name: "A" } }, "invalid_souls");
  const skipped = await bob.client.rpc("complete_onboarding", {});
  const pb = await profile(bob);
  check("everything skipped still completes", skipped.error === null && pb.onboarding_completed && pb.onboarding_reasons === null && pb.pace === null, skipped.error?.message);

  // 5. anon and the public projection
  const anonCall = await anon.rpc("complete_onboarding", {});
  check("anon is refused", anonCall.error !== null, "anon call succeeded");
  const handle = `onb${runId}`;
  const setHandle = await alice.client.rpc("set_handle", { p_handle: handle });
  const setPublic = await alice.client.from("profiles").update({ public_profile: true }).eq("user_id", alice.id);
  check("alice goes public", setHandle.error === null && setPublic.error === null, setHandle.error?.message ?? setPublic.error?.message);
  const pub = await anon.rpc("get_public_profile", { p_handle: handle });
  const body = JSON.stringify(pub.data ?? {});
  check("get_public_profile does not project the answers", pub.error === null && !body.includes("onboarding_reasons") && !body.includes("\"pace\""), pub.error?.message ?? body.slice(0, 200));
} finally {
  for (const u of [alice, bob, carol]) {
    if (!u) continue;
    const res = await deleteAccount(u.token).catch(() => undefined);
    if (!res?.ok) console.error(`cleanup: delete-account returned ${res?.status ?? "no response"}`);
  }
}

console.log(`\nSummary: passed=${passed} failed=${failed}`);
Deno.exit(failed === 0 ? 0 : 1);
