#!/usr/bin/env -S deno run --allow-env --allow-net
/**
 * Seeds the store-review demo account (#1449) on the REMOTE project, anon key only.
 *
 * Two accounts, both made up:
 *   - the reviewer (REVIEW_EMAIL): onboarding done, every consent the native gates ask for
 *     (terms, privacy, health data, 16+), about four weeks of pebbles with souls, cards and
 *     one collection, so Home, Path, the detail page and achievements all have something to show;
 *   - a friend (REVIEW_FRIEND_EMAIL), connected to the reviewer, so the reviewer finds
 *     Report in Connections (App Store Guideline 1.2).
 *
 * Every write goes through the RPCs the apps call (complete_onboarding, record_consent,
 * save_moment, complete_pebble, create/accept_connection_invite), so the account is shaped
 * exactly like one made in the app. Dates are relative to the run, so a fresh run always
 * reads as "the last four weeks".
 *
 * Idempotent: signs in when the account exists, signs up when it does not, records consents
 * again (record_consent is a no-op at the same version), seeds pebbles only into an empty
 * account, and connects only when no connection exists.
 * `--recreate` deletes both accounts through the real `delete-account` edge function first,
 * then builds them again: run it before each submission, or after a reviewer deleted or
 * changed the account.
 *
 * Run (from the main checkout root; `.env.demo` is gitignored by `.env*`):
 *   set -a; . ./.env; . ./.env.demo; set +a
 *   npm run db:seed:review --workspace=packages/supabase [-- --recreate]
 * `.env.demo` holds REVIEW_EMAIL, REVIEW_FRIEND_EMAIL and REVIEW_PASSWORD (one password for both).
 * The same email and password go in App Store Connect (App Review → Sign-in information)
 * and Play Console (App content → App access). Never commit them: this repo is public.
 */
import { createClient, type SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL");
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY");
const REVIEW_EMAIL = Deno.env.get("REVIEW_EMAIL");
const FRIEND_EMAIL = Deno.env.get("REVIEW_FRIEND_EMAIL");
const PASSWORD = Deno.env.get("REVIEW_PASSWORD");
if (!SUPABASE_URL || !ANON_KEY || !REVIEW_EMAIL || !FRIEND_EMAIL || !PASSWORD) {
  console.error("SUPABASE_URL, SUPABASE_ANON_KEY, REVIEW_EMAIL, REVIEW_FRIEND_EMAIL and REVIEW_PASSWORD must be set");
  Deno.exit(2);
}
const RECREATE = Deno.args.includes("--recreate");

// The versions the native consent gates require (apps/ios/Pebbles/Services/LegalVersions.swift
// and Android's twin). health_data and age_assurance are recorded against the privacy version.
const TERMS_VERSION = "1.2.0";
const PRIVACY_VERSION = "1.5.0";
const CONSENTS: { kind: string; version: string }[] = [
  { kind: "terms", version: TERMS_VERSION },
  { kind: "privacy", version: PRIVACY_VERSION },
  { kind: "health_data", version: PRIVACY_VERSION },
  { kind: "age_assurance", version: PRIVACY_VERSION },
];

type Account = { client: SupabaseClient; id: string; token: string; email: string };

function newClient(): SupabaseClient {
  return createClient(SUPABASE_URL!, ANON_KEY!, { auth: { persistSession: false } });
}

async function signIn(email: string): Promise<Account | null> {
  const client = newClient();
  const { data, error } = await client.auth.signInWithPassword({ email, password: PASSWORD! });
  if (error) {
    if (error.message.toLowerCase().includes("invalid login credentials")) return null;
    throw new Error(`sign in ${email}: ${error.message}`);
  }
  return { client, id: data.user.id, token: data.session.access_token, email };
}

async function signUp(email: string): Promise<Account> {
  const client = newClient();
  const { data, error } = await client.auth.signUp({ email, password: PASSWORD! });
  if (error) {
    // An existing account whose password is not REVIEW_PASSWORD: never guess, stop.
    throw new Error(`sign up ${email}: ${error.message} (if the account exists, REVIEW_PASSWORD does not match it)`);
  }
  if (!data.session || !data.user) throw new Error(`sign up ${email}: no session (is email confirmation on?)`);
  return { client, id: data.user.id, token: data.session.access_token, email };
}

async function deleteAccount(account: Account): Promise<void> {
  const res = await fetch(`${SUPABASE_URL}/functions/v1/delete-account`, {
    method: "POST",
    headers: { Authorization: `Bearer ${account.token}`, apikey: ANON_KEY!, "Content-Type": "application/json" },
  });
  if (!res.ok) throw new Error(`delete-account ${account.email}: ${res.status} ${await res.text()}`);
}

async function openAccount(email: string): Promise<{ account: Account; created: boolean }> {
  const existing = await signIn(email);
  if (existing && !RECREATE) return { account: existing, created: false };
  if (existing) {
    await deleteAccount(existing);
    console.log(`deleted ${email}`);
  }
  return { account: await signUp(email), created: true };
}

async function must<T>(label: string, call: PromiseLike<{ data: T | null; error: { message: string } | null }>): Promise<T> {
  const { data, error } = await call;
  if (error) throw new Error(`${label}: ${error.message}`);
  return data as T;
}

async function idsBySlug(client: SupabaseClient, table: "emotions" | "domains" | "card_types"): Promise<Map<string, string>> {
  const rows = await must<{ id: string; slug: string }[]>(table, client.from(table).select("id, slug"));
  return new Map(rows.map((r) => [r.slug, r.id]));
}

async function systemGlyphs(client: SupabaseClient, count: number): Promise<string[]> {
  const rows = await must<{ id: string }[]>(
    "glyphs",
    client.from("glyphs").select("id").eq("is_system", true).order("created_at").limit(count),
  );
  if (rows.length < count) throw new Error(`expected ${count} system glyphs, found ${rows.length}`);
  return rows.map((r) => r.id);
}

async function onboard(
  account: Account,
  name: string,
  reasons: string[],
  pace: string,
  souls: string[],
  glyphOffset: number,
): Promise<void> {
  const profile = await must<{ onboarding_completed: boolean }>(
    "profile",
    account.client.from("profiles").select("onboarding_completed").single(),
  );
  if (profile.onboarding_completed) return;
  const glyphs = await systemGlyphs(account.client, glyphOffset + 1 + souls.length);
  await must("complete_onboarding", account.client.rpc("complete_onboarding", {
    p_display_name: name,
    p_glyph_id: glyphs[glyphOffset],
    p_reasons: reasons,
    p_pace: pace,
    p_souls: souls.map((soul, i) => ({ name: soul, glyph_id: glyphs[glyphOffset + 1 + i] })),
  }));
}

async function consent(account: Account): Promise<void> {
  for (const c of CONSENTS) {
    await must(`record_consent ${c.kind}`, account.client.rpc("record_consent", {
      p_kind: c.kind,
      p_document_version: c.version,
      p_source: "ios_settings",
    }));
  }
}

// ---------------------------------------------------------------------------
// The reviewer's four weeks. Ordinary, calm, made up. No health details, no
// crisis content: reviewers read every screen.
// ---------------------------------------------------------------------------
type Seed = {
  daysAgo: number;
  hour: number;
  name: string;
  emotion: string;
  intensity: 1 | 2 | 3;
  positiveness: -1 | 0 | 1;
  domains: string[];
  souls?: string[];
  collection?: string;
  card?: { type: "free" | "feelings" | "thoughts" | "behaviour"; value: string };
};

const SEEDS: Seed[] = [
  { daysAgo: 27, hour: 9, name: "First swim of the season", emotion: "excited", intensity: 2, positiveness: 1, domains: ["fitness"], card: { type: "feelings", value: "Cold for ten seconds, then wide awake for the whole day." } },
  { daysAgo: 25, hour: 20, name: "Dinner at Jules's new place", emotion: "joyful", intensity: 3, positiveness: 1, domains: ["friends"], souls: ["Jules", "Inès"] },
  { daysAgo: 23, hour: 18, name: "Deadline moved up a week", emotion: "stressed", intensity: 2, positiveness: -1, domains: ["work"], card: { type: "thoughts", value: "It is a lot, but it is one week. Make a list tomorrow morning." } },
  { daysAgo: 21, hour: 11, name: "Sunday walk along the river", emotion: "peaceful", intensity: 2, positiveness: 1, domains: ["selfcare"], souls: ["Mum"], collection: "Weekend walks" },
  { daysAgo: 19, hour: 13, name: "Long call with Mum", emotion: "grateful", intensity: 2, positiveness: 1, domains: ["family"], souls: ["Mum"] },
  { daysAgo: 17, hour: 22, name: "Couldn't switch off after work", emotion: "drained", intensity: 1, positiveness: -1, domains: ["work"], card: { type: "behaviour", value: "Closed the laptop and read for twenty minutes instead." } },
  { daysAgo: 15, hour: 17, name: "Shipped the project", emotion: "proud", intensity: 3, positiveness: 1, domains: ["work"], souls: ["Marco"] },
  { daysAgo: 14, hour: 10, name: "Market morning", emotion: "content", intensity: 1, positiveness: 1, domains: ["selfcare"], souls: ["Inès"], collection: "Weekend walks" },
  { daysAgo: 12, hour: 19, name: "Guitar lesson, finally clean chords", emotion: "satisfied", intensity: 2, positiveness: 1, domains: ["hobbies"], card: { type: "free", value: "Three weeks of practice for one clean change. Worth it." } },
  { daysAgo: 10, hour: 8, name: "Missed the train", emotion: "annoyed", intensity: 1, positiveness: 0, domains: ["tasks"] },
  { daysAgo: 8, hour: 21, name: "Board games night", emotion: "amused", intensity: 2, positiveness: 1, domains: ["friends"], souls: ["Jules", "Marco"] },
  { daysAgo: 7, hour: 12, name: "Forest loop with Inès", emotion: "calm", intensity: 2, positiveness: 1, domains: ["selfcare", "friends"], souls: ["Inès"], collection: "Weekend walks" },
  { daysAgo: 5, hour: 18, name: "Started the pottery class", emotion: "hopeful", intensity: 2, positiveness: 1, domains: ["hobbies", "education"], card: { type: "feelings", value: "Nervous walking in, light walking out." } },
  { daysAgo: 3, hour: 20, name: "Cooked Grandma's soup", emotion: "content", intensity: 2, positiveness: 1, domains: ["family"], souls: ["Mum"] },
  { daysAgo: 1, hour: 9, name: "Quiet morning, coffee on the balcony", emotion: "calm", intensity: 1, positiveness: 1, domains: ["selfcare"] },
];

function happenedAt(daysAgo: number, hour: number): string {
  const now = new Date();
  const d = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() - daysAgo, hour - 1, 0, 0));
  return d.toISOString().replace(/\.\d{3}Z$/, "Z"); // whole seconds: the cross-surface rule
}

// Every read filters on user_id: moments and pebbles RLS also returns other people's public pebbles.
async function nameMap(account: Account, table: "souls" | "collections"): Promise<Map<string, string>> {
  const rows = await must<{ id: string; name: string }[]>(table, account.client.from(table).select("id, name").eq("user_id", account.id));
  return new Map(rows.map((r) => [r.name, r.id]));
}

function lookup(map: Map<string, string>, key: string, what: string): string {
  const id = map.get(key);
  if (!id) throw new Error(`unknown ${what}: ${key}`);
  return id;
}

async function seedPebbles(account: Account): Promise<number> {
  const { count, error } = await account.client.from("moments").select("id", { count: "exact", head: true }).eq("user_id", account.id);
  if (error) throw new Error(`moments count: ${error.message}`);
  if ((count ?? 0) > 0) {
    console.log(`${account.email} already has ${count} moments: not seeding (use --recreate to start over)`);
    return 0;
  }
  const emotions = await idsBySlug(account.client, "emotions");
  const domains = await idsBySlug(account.client, "domains");
  const cardTypes = await idsBySlug(account.client, "card_types");
  let souls = await nameMap(account, "souls");
  let collections = await nameMap(account, "collections");

  for (const seed of SEEDS) {
    const momentId = await must<string>(`save_moment ${seed.name}`, account.client.rpc("save_moment", {
      payload: { name: seed.name, happened_at: happenedAt(seed.daysAgo, seed.hour) },
    }));
    const known = (seed.souls ?? []).filter((s) => souls.has(s));
    const fresh = (seed.souls ?? []).filter((s) => !souls.has(s));
    const payload: Record<string, unknown> = {
      moment_id: momentId,
      emotion_id: lookup(emotions, seed.emotion, "emotion"),
      intensity: seed.intensity,
      positiveness: seed.positiveness,
      domain_ids: seed.domains.map((d) => lookup(domains, d, "domain")),
    };
    if (seed.souls) {
      payload.soul_ids = known.map((s) => souls.get(s));
      payload.new_souls = fresh.map((name) => ({ name }));
    }
    if (seed.collection) {
      const id = collections.get(seed.collection);
      payload.collection_ids = id ? [id] : [];
      payload.new_collections = id ? [] : [{ name: seed.collection }];
    }
    if (seed.card) {
      payload.cards = [{ species_id: lookup(cardTypes, seed.card.type, "card type"), value: seed.card.value, sort_order: 0 }];
    }
    await must(`complete_pebble ${seed.name}`, account.client.rpc("complete_pebble", { payload }));
    if (fresh.length > 0) souls = await nameMap(account, "souls");
    if (seed.collection && !collections.has(seed.collection)) collections = await nameMap(account, "collections");
  }
  return SEEDS.length;
}

async function connect(reviewer: Account, friend: Account): Promise<boolean> {
  const connections = await must<unknown[]>("get_connections", reviewer.client.rpc("get_connections"));
  if (connections.length > 0) return false;
  const invite = await must<{ token: string }>("create_connection_invite", friend.client.rpc("create_connection_invite"));
  await must("accept_connection_invite", reviewer.client.rpc("accept_connection_invite", { p_token: invite.token }));
  return true;
}

try {
  const { account: reviewer, created: reviewerCreated } = await openAccount(REVIEW_EMAIL);
  const { account: friend, created: friendCreated } = await openAccount(FRIEND_EMAIL);
  console.log(`${REVIEW_EMAIL}: ${reviewerCreated ? "created" : "signed in"}`);
  console.log(`${FRIEND_EMAIL}: ${friendCreated ? "created" : "signed in"}`);

  await onboard(reviewer, "Sam", ["notice-feelings", "keep-people", "good-days"], "few-a-week", ["Mum", "Jules", "Inès"], 0);
  await onboard(friend, "Noa", ["keep-people"], "weekly-wrap", [], 4);
  await consent(reviewer);
  await consent(friend);
  console.log("onboarding and consents: done");

  const seeded = await seedPebbles(reviewer);
  if (seeded > 0) console.log(`seeded ${seeded} pebbles`);
  console.log((await connect(reviewer, friend)) ? "connected Sam and Noa" : "already connected");

  const { count } = await reviewer.client.from("pebbles").select("id", { count: "exact", head: true }).eq("user_id", reviewer.id);
  console.log(`ready: ${REVIEW_EMAIL} has ${count ?? 0} pebbles and one connection`);
} catch (e) {
  console.error(e instanceof Error ? e.message : e);
  Deno.exit(1);
}
