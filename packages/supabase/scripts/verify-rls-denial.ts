#!/usr/bin/env -S deno run --allow-env --allow-net --allow-read --allow-run
/**
 * Schema-driven RLS denial harness (#1523) — runs against a LOCAL stack only.
 *
 * The other harnesses each prove one contract on the linked project. This one
 * proves the floor under all of them: no signed-in stranger and no anonymous
 * visitor can read or write another user's rows, on EVERY table, and no
 * non-owner can drive a SECURITY DEFINER function onto someone else's data.
 *
 *   1. Inventory — `rls-inventory.sql` lists the live catalog: every `public`
 *      table and view, every SECURITY DEFINER function in `public`/`private`.
 *   2. Registry — `rls-expectations.json` must hold one entry per inventoried
 *      object and no stale ones. An object with no entry fails the run: a new
 *      table or definer RPC cannot ship without someone writing down who may
 *      touch it. Catalog facts the entry implies are asserted here (RLS on,
 *      trigger functions and service-only functions not executable by anon or
 *      authenticated, owner-only functions not executable by anon, the view's
 *      security_invoker flag as registered).
 *   3. Probes — four throwaway users: A (owner), B (stranger), C (A's friend,
 *      who owns a public pebble), D (whom A blocks). A's world is seeded
 *      through the same RPCs the clients use (save_moment, complete_pebble,
 *      create_connection_invite, record_consent, …); single-table rows a
 *      client inserts directly (souls, collections, glyphs) are inserted
 *      directly. Then, per table, anon and B try to select, update, delete
 *      A's row and insert a row forged as A's; per function, anon and B call
 *      it with A's ids. A service connection (`psql $DATABASE_URL`) witnesses
 *      that each seeded row exists before the probes and survives them.
 *
 * Run (local stack: `supabase start`, then `supabase db reset`):
 *   eval "$(supabase status -o env | grep -E '^(API_URL|ANON_KEY|DB_URL)=')"
 *   SUPABASE_URL=$API_URL SUPABASE_ANON_KEY=$ANON_KEY DATABASE_URL=$DB_URL \
 *     npm run db:verify:rls --workspace=packages/supabase
 *
 * REFUSES to run unless SUPABASE_URL and DATABASE_URL both point at 127.0.0.1
 * or localhost: it holds a superuser connection and deletes what it made with
 * it. It must never touch the linked project. Exits non-zero on any failure.
 */

import { createClient, type SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";
import { type InventoryRow, loadInventory, objectKey } from "./rls-inventory.ts";

// ---------------------------------------------------------------------------
// Local-only guard: before anything else touches the network.
// ---------------------------------------------------------------------------

function isLocal(url: string | undefined): boolean {
  if (!url) return false;
  try {
    const host = new URL(url).hostname;
    return host === "127.0.0.1" || host === "localhost";
  } catch {
    return false;
  }
}

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
const DATABASE_URL = Deno.env.get("DATABASE_URL") ?? "";

if (!isLocal(SUPABASE_URL) || !isLocal(DATABASE_URL)) {
  console.error(
    "verify-rls-denial runs against a LOCAL stack only: SUPABASE_URL and DATABASE_URL must point at 127.0.0.1 or localhost.",
  );
  Deno.exit(2);
}
if (!ANON_KEY) {
  console.error("SUPABASE_ANON_KEY must be set (supabase status -o env)");
  Deno.exit(2);
}

// ---------------------------------------------------------------------------
// Reporting
// ---------------------------------------------------------------------------

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
const unwitnessed: string[] = [];

// ---------------------------------------------------------------------------
// Registry: shape and validation (JSON.parse is untyped; nothing is asserted)
// ---------------------------------------------------------------------------

type Access = "none" | "own" | "public-rows" | "all";
type Ops = { select: Access; insert: Access; update: Access; delete: Access };
type Seed = { via: "rpc" | "insert" | "trigger"; name: string } | "reference" | "none";
type TableEntry = {
  anon: Ops;
  authenticated: Ops;
  seed: Seed;
  key: Record<string, string> | null;
  rowIsPublic: boolean;
  forge: Record<string, unknown> | null;
  update: Record<string, unknown> | null;
  note: string;
};
type ViewAccess = "none" | "own" | "public-rows" | "all";
type ViewEntry = {
  securityInvoker: boolean;
  anon: ViewAccess;
  authenticated: ViewAccess;
  key: Record<string, string> | null;
  note: string;
};
type FunctionKind = "owner-only" | "admin-only" | "trigger" | "public-read" | "service-only" | "internal";
type FunctionEntry = {
  kind: FunctionKind;
  args: Record<string, unknown> | null;
  nullArgs: Record<string, unknown> | null;
  intact: string[];
  note: string;
};
type Registry = {
  tables: Map<string, TableEntry>;
  views: Map<string, ViewEntry>;
  functions: Map<string, FunctionEntry>;
};

type Json = null | boolean | number | string | Json[] | { [k: string]: Json };

function isObject(v: unknown): v is Record<string, unknown> {
  return typeof v === "object" && v !== null && !Array.isArray(v);
}

const ACCESS: readonly string[] = ["none", "own", "public-rows", "all"];
const KINDS: readonly string[] = ["owner-only", "admin-only", "trigger", "public-read", "service-only", "internal"];

class RegistryError extends Error {}

function str(v: unknown, at: string): string {
  if (typeof v !== "string" || v === "") throw new RegistryError(`${at}: expected a non-empty string`);
  if (v === "TODO") throw new RegistryError(`${at}: still TODO`);
  return v;
}

function access(v: unknown, at: string): Access {
  const s = str(v, at);
  if (s === "none" || s === "own" || s === "public-rows" || s === "all") return s;
  throw new RegistryError(`${at}: '${s}' is not one of ${ACCESS.join(", ")}`);
}

function ops(v: unknown, at: string): Ops {
  if (!isObject(v)) throw new RegistryError(`${at}: expected {select, insert, update, delete}`);
  return {
    select: access(v.select, `${at}.select`),
    insert: access(v.insert, `${at}.insert`),
    update: access(v.update, `${at}.update`),
    delete: access(v.delete, `${at}.delete`),
  };
}

function record(v: unknown, at: string): Record<string, unknown> | null {
  if (v === undefined) return null;
  if (!isObject(v)) throw new RegistryError(`${at}: expected an object`);
  if (JSON.stringify(v).includes('"TODO"')) throw new RegistryError(`${at}: still TODO`);
  return v;
}

function stringRecord(v: unknown, at: string): Record<string, string> | null {
  const r = record(v, at);
  if (r === null) return null;
  const out: Record<string, string> = {};
  for (const [k, val] of Object.entries(r)) out[k] = str(val, `${at}.${k}`);
  return out;
}

function seed(v: unknown, at: string): Seed {
  if (v === "reference" || v === "none") return v;
  if (!isObject(v)) throw new RegistryError(`${at}: expected "reference", "none" or {via, name}`);
  const via = str(v.via, `${at}.via`);
  if (via !== "rpc" && via !== "insert" && via !== "trigger") throw new RegistryError(`${at}.via: '${via}'`);
  return { via, name: str(v.name, `${at}.name`) };
}

function parseRegistry(raw: unknown): Registry {
  if (!isObject(raw)) throw new RegistryError("registry: expected an object");
  const tables = new Map<string, TableEntry>();
  const views = new Map<string, ViewEntry>();
  const functions = new Map<string, FunctionEntry>();

  if (!isObject(raw.tables)) throw new RegistryError("registry.tables: expected an object");
  for (const [name, v] of Object.entries(raw.tables)) {
    const at = `tables.${name}`;
    if (!isObject(v)) throw new RegistryError(`${at}: expected an object`);
    const entry: TableEntry = {
      anon: ops(v.anon, `${at}.anon`),
      authenticated: ops(v.authenticated, `${at}.authenticated`),
      seed: seed(v.seed, `${at}.seed`),
      key: stringRecord(v.key, `${at}.key`),
      rowIsPublic: v.row_is_public === true,
      forge: record(v.forge, `${at}.forge`),
      update: record(v.update, `${at}.update`),
      note: str(v.note, `${at}.note`),
    };
    if (entry.seed !== "none" && (!entry.key || !entry.forge || !entry.update)) {
      throw new RegistryError(`${at}: a seeded or reference table needs key, forge and update`);
    }
    tables.set(name, entry);
  }

  if (!isObject(raw.views)) throw new RegistryError("registry.views: expected an object");
  for (const [name, v] of Object.entries(raw.views)) {
    const at = `views.${name}`;
    if (!isObject(v)) throw new RegistryError(`${at}: expected an object`);
    if (typeof v.security_invoker !== "boolean") throw new RegistryError(`${at}.security_invoker: expected a boolean`);
    const anon = access(v.anon, `${at}.anon`);
    const authenticated = access(v.authenticated, `${at}.authenticated`);
    views.set(name, {
      securityInvoker: v.security_invoker,
      anon,
      authenticated,
      key: stringRecord(v.key, `${at}.key`),
      note: str(v.note, `${at}.note`),
    });
  }

  if (!isObject(raw.functions)) throw new RegistryError("registry.functions: expected an object");
  for (const [name, v] of Object.entries(raw.functions)) {
    const at = `functions.${name}`;
    if (!isObject(v)) throw new RegistryError(`${at}: expected an object`);
    const kind = str(v.kind, `${at}.kind`);
    if (!isFunctionKind(kind)) throw new RegistryError(`${at}.kind: '${kind}' is not one of ${KINDS.join(", ")}`);
    const intactRaw = v.intact ?? [];
    if (!Array.isArray(intactRaw)) throw new RegistryError(`${at}.intact: expected an array`);
    const entry: FunctionEntry = {
      kind,
      args: record(v.args, `${at}.args`),
      nullArgs: record(v.null_args, `${at}.null_args`),
      intact: intactRaw.map((t, i) => str(t, `${at}.intact[${i}]`)),
      note: str(v.note, `${at}.note`),
    };
    if (kind !== "trigger" && entry.args === null) {
      throw new RegistryError(`${at}: every callable function needs args (use {} for none)`);
    }
    functions.set(name, entry);
  }
  return { tables, views, functions };
}

function isFunctionKind(s: string): s is FunctionKind {
  return KINDS.includes(s);
}

// ---------------------------------------------------------------------------
// Service connection (local superuser): witnesses, reference rows, cleanup
// ---------------------------------------------------------------------------

async function sql(query: string): Promise<string> {
  const out = await new Deno.Command("psql", {
    args: [DATABASE_URL, "-X", "-q", "-At", "-v", "ON_ERROR_STOP=1", "-c", query],
    stdout: "piped",
    stderr: "piped",
  }).output();
  if (!out.success) throw new Error(`psql: ${new TextDecoder().decode(out.stderr).trim()}`);
  return new TextDecoder().decode(out.stdout).trim();
}

function literal(v: string): string {
  return `'${v.replaceAll("'", "''")}'`;
}

function ident(v: string): string {
  if (!/^[a-z_][a-z0-9_]*$/.test(v)) throw new Error(`unsafe identifier '${v}'`);
  return `"${v}"`;
}

function whereClause(filters: Record<string, string>): string {
  return Object.entries(filters).map(([col, val]) => `${ident(col)}::text = ${literal(val)}`).join(" and ");
}

async function witness(table: string, filters: Record<string, string>): Promise<number> {
  return Number(await sql(`select count(*) from public.${ident(table)} where ${whereClause(filters)}`));
}

// ---------------------------------------------------------------------------
// Fixture context: `$name` references in the registry resolve here
// ---------------------------------------------------------------------------

const ctx = new Map<string, string>();

function resolve(value: unknown): unknown {
  if (typeof value === "string" && value.startsWith("$")) {
    const v = ctx.get(value.slice(1));
    if (v === undefined) throw new Error(`unknown fixture reference ${value}`);
    return v;
  }
  if (Array.isArray(value)) return value.map(resolve);
  if (isObject(value)) {
    const out: Record<string, unknown> = {};
    for (const [k, v] of Object.entries(value)) out[k] = resolve(v);
    return out;
  }
  return value;
}

async function resolveKey(table: string, key: Record<string, string>): Promise<Record<string, string>> {
  const out: Record<string, string> = {};
  for (const [col, val] of Object.entries(key)) {
    if (val === "@first") {
      // A reference row: the first one the table holds, read as superuser.
      const first = await sql(`select ${ident(col)}::text from public.${ident(table)} order by 1 limit 1`);
      if (first === "") throw new Error(`public.${table} holds no row to probe`);
      out[col] = first;
    } else {
      const r = resolve(val);
      if (typeof r !== "string") throw new Error(`key ${table}.${col} did not resolve to a string`);
      out[col] = r;
    }
  }
  return out;
}

// ---------------------------------------------------------------------------
// Users
// ---------------------------------------------------------------------------

const runId = crypto.randomUUID().slice(0, 8);
const password = `Rls-${crypto.randomUUID()}`;
type TestUser = { client: SupabaseClient; id: string };
const users: TestUser[] = [];

async function signUp(label: string): Promise<TestUser> {
  const client = createClient(SUPABASE_URL, ANON_KEY, { auth: { persistSession: false } });
  const email = `rls-verify-${label}-${runId}@example.test`;
  const { data, error } = await client.auth.signUp({ email, password });
  if (error || !data.session || !data.user) {
    throw new Error(`signUp ${label}: ${error?.message ?? "no session (email confirmations on?)"}`);
  }
  const user = { client, id: data.user.id };
  users.push(user);
  return user;
}

const anon = createClient(SUPABASE_URL, ANON_KEY, { auth: { persistSession: false } });

type RpcResult = { data: unknown; error: { code?: string; message: string } | null };

async function rpc(client: SupabaseClient, name: string, args: Record<string, unknown>): Promise<RpcResult> {
  const { data, error } = await client.rpc(name, args);
  return { data, error };
}

async function must(label: string, p: PromiseLike<RpcResult>): Promise<unknown> {
  const r = await p;
  if (r.error) throw new Error(`${label}: ${r.error.message}`);
  return r.data;
}

function idOf(label: string, data: unknown): string {
  if (typeof data === "string") return data;
  if (isObject(data) && typeof data.id === "string") return data.id;
  if (Array.isArray(data) && isObject(data[0]) && typeof data[0].id === "string") return data[0].id;
  throw new Error(`${label}: no id in ${JSON.stringify(data)}`);
}

function field(label: string, data: unknown, name: string): string {
  if (isObject(data) && typeof data[name] === "string") return data[name];
  throw new Error(`${label}: no ${name} in ${JSON.stringify(data)}`);
}

// ---------------------------------------------------------------------------
// Seeding A's world (every write a client makes, the way it makes it)
// ---------------------------------------------------------------------------

const STROKES = [{ d: "M10,10 L90,90", width: 6 }];

async function seedWorld(a: TestUser, b: TestUser, c: TestUser, d: TestUser) {
  ctx.set("A", a.id);
  ctx.set("B", b.id);
  ctx.set("C", c.id);
  ctx.set("D", d.id);

  const emotion = await sql("select id from public.emotions order by slug limit 1");
  const domain = await sql("select id from public.domains order by slug limit 1");
  const cardType = await sql("select id from public.card_types order by slug limit 1");
  const systemGlyph = await sql("select id from public.glyphs where is_system order by id limit 1");
  ctx.set("emotion", emotion);
  ctx.set("domain", domain);
  ctx.set("system_glyph", systemGlyph);
  ctx.set("card_type", cardType);
  ctx.set("emotion_category", await sql("select id from public.emotion_categories order by slug limit 1"));
  ctx.set("any_achievement", await sql("select id from public.achievements order by slug limit 1"));

  // Profile: handle + public profile, a direct single-table update (the clients' path).
  const handle = `rlsa${runId}`;
  const prof = await a.client.from("profiles").update({ handle, public_profile: true }).eq("user_id", a.id);
  if (prof.error) throw new Error(`profile handle: ${prof.error.message}`);
  ctx.set("handle_A", handle);

  // Direct single-table inserts (no RPC exists; one statement each).
  const soul = await a.client.from("souls").insert({ user_id: a.id, name: `soul ${runId}` }).select("id").single();
  ctx.set("soul", idOf("souls insert", soul.data ?? soul.error?.message));
  const coll = await a.client.from("collections").insert({ user_id: a.id, name: `coll ${runId}` }).select("id")
    .single();
  ctx.set("collection", idOf("collections insert", coll.data ?? coll.error?.message));
  const glyph = await a.client.from("glyphs")
    .insert({ user_id: a.id, name: `glyph ${runId}`, strokes: STROKES, view_box: "0 0 100 100" }).select("id").single();
  ctx.set("glyph", idOf("glyphs insert", glyph.data ?? glyph.error?.message));
  const glyph2 = await a.client.from("glyphs")
    .insert({ user_id: a.id, name: `glyph2 ${runId}`, strokes: STROKES, view_box: "0 0 100 100" }).select("id")
    .single();
  ctx.set("glyph_submitted", idOf("glyphs insert 2", glyph2.data ?? glyph2.error?.message));
  const fav = await a.client.from("glyph_favourites").insert({ user_id: a.id, glyph_id: systemGlyph });
  if (fav.error) throw new Error(`glyph_favourites insert: ${fav.error.message}`);

  // RPCs.
  const submission = await must(
    "submit_glyph",
    rpc(a.client, "submit_glyph", { p_glyph_id: ctx.get("glyph_submitted") }),
  );
  ctx.set("submission", idOf("submit_glyph", submission));

  const momentId = idOf(
    "save_moment",
    await must(
      "save_moment",
      rpc(a.client, "save_moment", {
        payload: {
          name: `moment ${runId}`,
          happened_at: "2026-10-11T10:00:00Z",
          snaps: [{ storage_path: `${a.id}/rls-${runId}.jpg` }],
        },
      }),
    ),
  );
  ctx.set("moment", momentId);
  ctx.set("snap", await sql(`select id from public.snaps where moment_id = ${literal(momentId)} limit 1`));

  const pebbleId = idOf(
    "complete_pebble",
    await must(
      "complete_pebble",
      rpc(a.client, "complete_pebble", {
        payload: {
          moment_id: momentId,
          emotion_id: emotion,
          intensity: 2,
          positiveness: 1,
          visibility: "secret",
          domain_ids: [domain],
          soul_ids: [ctx.get("soul")],
          collection_ids: [ctx.get("collection")],
          cards: [{ species_id: cardType, value: `card ${runId}` }],
        },
      }),
    ),
  );
  ctx.set("pebble", pebbleId);

  await must("record_consent", rpc(a.client, "record_consent", { p_kind: "terms", p_document_version: "1.0.0", p_source: "web_register" }));
  await must("mark_achievements_seen", rpc(a.client, "mark_achievements_seen", { p_card: "explorer" }));
  await must("reveal_wrap", rpc(a.client, "reveal_wrap", { p_month: "2026-01-01", p_tz: "UTC" }));
  await must("check_achievements", rpc(a.client, "check_achievements", {}));

  // Connections: D accepts, then A removes D with a block; C accepts and stays.
  const inviteD = await must("create_connection_invite (D)", rpc(a.client, "create_connection_invite", { p_rotate: false }));
  await must("accept (D)", rpc(d.client, "accept_connection_invite", { p_token: field("invite", inviteD, "token") }));
  const connD = await sql(
    `select id from public.connections where ${literal(a.id)} in (user_a, user_b) and ${literal(d.id)} in (user_a, user_b)`,
  );
  await must("remove_connection (block D)", rpc(a.client, "remove_connection", { p_connection_id: connD, p_block: true }));
  const inviteC = await must("create_connection_invite (C)", rpc(a.client, "create_connection_invite", { p_rotate: true }));
  await must("accept (C)", rpc(c.client, "accept_connection_invite", { p_token: field("invite", inviteC, "token") }));
  ctx.set(
    "connection",
    await sql(
      `select id from public.connections where ${literal(a.id)} in (user_a, user_b) and ${literal(c.id)} in (user_a, user_b)`,
    ),
  );
  // A live token for the preview probe (accepting consumed nothing: the invite is reusable or rotated).
  const inviteLive = await must("create_connection_invite (live)", rpc(a.client, "create_connection_invite", { p_rotate: false }));
  ctx.set("token", field("invite", inviteLive, "token"));

  // C's public pebble, which A reports (a report needs a target A can see).
  const cMoment = idOf(
    "save_moment (C)",
    await must("save_moment (C)", rpc(c.client, "save_moment", { payload: { name: `public ${runId}`, happened_at: "2026-10-12T10:00:00Z" } })),
  );
  const cPebble = idOf(
    "complete_pebble (C)",
    await must(
      "complete_pebble (C)",
      rpc(c.client, "complete_pebble", {
        payload: { moment_id: cMoment, emotion_id: emotion, intensity: 1, positiveness: 0, visibility: "public", domain_ids: [domain] },
      }),
    ),
  );
  ctx.set("public_pebble", cPebble);
  const report = await must(
    "report_content",
    rpc(a.client, "report_content", { p_target_kind: "pebble", p_target_id: cPebble, p_reason: "spam", p_detail: null }),
  );
  ctx.set("report", idOf("report_content", report));
}

// ---------------------------------------------------------------------------
// Probes
// ---------------------------------------------------------------------------

type Actor = { label: "anon" | "stranger"; client: SupabaseClient };

function denied(error: { code?: string } | null): boolean {
  return error !== null && error.code === "42501";
}

function rowsOf(data: unknown): number {
  return Array.isArray(data) ? data.length : 0;
}

function expectVisible(access: Access, rowIsPublic: boolean): boolean {
  return access === "all" || (access === "public-rows" && rowIsPublic);
}

async function probeTable(name: string, entry: TableEntry, actors: Actor[]) {
  if (entry.seed === "none" || !entry.key || !entry.forge || !entry.update) {
    // No row to aim at: the blanket read is still asserted.
    for (const actor of actors) {
      const ops = actor.label === "anon" ? entry.anon : entry.authenticated;
      if (ops.select === "all") continue;
      const { data, error } = await actor.client.from(name).select("*").limit(1);
      check(`${name}: ${actor.label} reads nothing`, denied(error) || (!error && rowsOf(data) === 0), error?.message);
    }
    unwitnessed.push(name);
    return;
  }
  const key = await resolveKey(name, entry.key);
  const before = await witness(name, key);
  check(`${name}: seed row exists (${entry.seed === "reference" ? "reference" : `${entry.seed.via} ${entry.seed.name}`})`, before > 0, `0 rows for ${JSON.stringify(key)}`);
  if (before === 0) return;

  for (const actor of actors) {
    const ops = actor.label === "anon" ? entry.anon : entry.authenticated;
    const who = actor.label;

    // select
    let q = actor.client.from(name).select("*");
    for (const [col, val] of Object.entries(key)) q = q.eq(col, val);
    const sel = await q;
    const visible = !sel.error && rowsOf(sel.data) > 0;
    if (expectVisible(ops.select, entry.rowIsPublic)) {
      check(`${name}: ${who} select sees the ${entry.rowIsPublic ? "public " : ""}row`, visible, sel.error?.message);
    } else {
      check(
        `${name}: ${who} select is denied`,
        !visible && (sel.error === null || denied(sel.error)),
        sel.error?.message ?? `${rowsOf(sel.data)} row(s) visible`,
      );
    }

    // update
    let u = actor.client.from(name).update(entry.update);
    for (const [col, val] of Object.entries(key)) u = u.eq(col, val);
    const upd = await u.select();
    check(
      `${name}: ${who} update is denied`,
      denied(upd.error) || (upd.error === null && rowsOf(upd.data) === 0),
      upd.error ? `${upd.error.code}: ${upd.error.message}` : `${rowsOf(upd.data)} row(s) updated`,
    );

    // delete
    let del = actor.client.from(name).delete();
    for (const [col, val] of Object.entries(key)) del = del.eq(col, val);
    const dr = await del.select();
    check(
      `${name}: ${who} delete is denied`,
      denied(dr.error) || (dr.error === null && rowsOf(dr.data) === 0),
      dr.error ? `${dr.error.code}: ${dr.error.message}` : `${rowsOf(dr.data)} row(s) deleted`,
    );

    // insert a row forged as the owner's
    const forged = resolve(entry.forge);
    const ins = await actor.client.from(name).insert(isObject(forged) ? forged : {}).select();
    check(
      `${name}: ${who} forged insert is denied by RLS or grant`,
      denied(ins.error),
      ins.error ? `${ins.error.code}: ${ins.error.message}` : `${rowsOf(ins.data)} row(s) inserted`,
    );
  }
  const after = await witness(name, key);
  check(`${name}: the owner's row survived the probes`, after === before, `${before} → ${after}`);
}

async function probeView(name: string, entry: ViewEntry, actors: Actor[]) {
  for (const actor of actors) {
    const access = actor.label === "anon" ? entry.anon : entry.authenticated;
    let q = actor.client.from(name).select("*").limit(5);
    if (entry.key) {
      for (const [col, val] of Object.entries(await resolveKey(name, entry.key))) q = q.eq(col, val);
    }
    const { data, error } = await q;
    if (access === "all") {
      check(`${name}: ${actor.label} reads the view`, error === null, error?.message);
    } else if (access === "none" && !entry.key) {
      check(`${name}: ${actor.label} reads nothing`, denied(error) || (error === null && rowsOf(data) === 0), error?.message);
    } else {
      check(
        `${name}: ${actor.label} reads none of the owner's rows`,
        denied(error) || (error === null && rowsOf(data) === 0),
        error?.message ?? `${rowsOf(data)} row(s)`,
      );
    }
  }
}

function keysDeep(v: unknown, into: Set<string>) {
  if (Array.isArray(v)) {
    for (const x of v) keysDeep(x, into);
  } else if (isObject(v)) {
    for (const [k, x] of Object.entries(v)) {
      into.add(k);
      keysDeep(x, into);
    }
  }
}

async function probeFunction(
  key: string,
  row: InventoryRow,
  entry: FunctionEntry,
  stranger: TestUser,
  tables: Map<string, TableEntry>,
) {
  const name = row.name;
  const label = `${key}()`;
  switch (entry.kind) {
    case "trigger":
      check(`${label}: is a trigger function`, row.returnsTrigger === true);
      check(
        `${label}: execute revoked from anon and authenticated`,
        row.anonExecute === false && row.authenticatedExecute === false,
        `anon=${row.anonExecute} authenticated=${row.authenticatedExecute}`,
      );
      return;
    case "service-only": {
      check(
        `${label}: execute revoked from anon and authenticated`,
        row.anonExecute === false && row.authenticatedExecute === false,
        `anon=${row.anonExecute} authenticated=${row.authenticatedExecute}`,
      );
      const r = await rpc(stranger.client, name, isObject(resolve(entry.args)) ? argsOf(entry) : {});
      check(`${label}: stranger call is refused`, denied(r.error), r.error ? `${r.error.code}: ${r.error.message}` : "returned");
      return;
    }
    case "internal": {
      check(`${label}: lives outside the API schema`, row.schema !== "public", row.schema);
      const r = await stranger.client.schema(row.schema).rpc(name, argsOf(entry));
      check(`${label}: not callable through the API`, r.error !== null, "returned");
      return;
    }
    case "admin-only": {
      for (const [who, client] of [["anon", anon], ["stranger", stranger.client]] as const) {
        const r = await rpc(client, name, argsOf(entry));
        check(`${label}: ${who} call is refused`, denied(r.error), r.error ? `${r.error.code}: ${r.error.message}` : "returned");
      }
      return;
    }
    case "owner-only": {
      check(`${label}: execute revoked from anon`, row.anonExecute === false, `anon=${row.anonExecute}`);
      const a = await rpc(anon, name, argsOf(entry));
      check(`${label}: anon call is refused`, denied(a.error), a.error ? `${a.error.code}: ${a.error.message}` : "returned");
      const r = await rpc(stranger.client, name, argsOf(entry));
      const empty = r.error === null &&
        (r.data === null || (Array.isArray(r.data) && r.data.length === 0) || r.data === false);
      if (entry.intact.length > 0) {
        check(
          `${label}: stranger call on the owner's ids is refused or a no-op`,
          r.error !== null || empty,
          `returned ${JSON.stringify(r.data)}`,
        );
        for (const t of entry.intact) {
          const te = tables.get(t);
          if (!te?.key) {
            check(`${label}: intact table ${t} has a key`, false);
            continue;
          }
          const n = await witness(t, await resolveKey(t, te.key));
          check(`${label}: the owner's ${t} row is intact`, n > 0);
        }
      }
      return;
    }
    case "public-read": {
      const r = await rpc(anon, name, argsOf(entry));
      check(`${label}: anon call succeeds`, r.error === null, r.error?.message);
      const keys = new Set<string>();
      keysDeep(r.data, keys);
      check(`${label}: projection carries no user_id`, !keys.has("user_id"), [...keys].join(", "));
      if (entry.nullArgs) {
        const resolved = resolve(entry.nullArgs);
        const n = await rpc(anon, name, isObject(resolved) ? resolved : {});
        check(`${label}: a private target reads as null`, n.error === null && n.data === null, JSON.stringify(n.data));
      }
      return;
    }
  }
}

function argsOf(entry: FunctionEntry): Record<string, unknown> {
  const r = resolve(entry.args ?? {});
  return isObject(r) ? r : {};
}

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------

async function cleanup() {
  for (const u of users) {
    try {
      await sql(`select public.purge_account(${literal(u.id)})`);
    } catch (e) {
      console.log(`  (cleanup: purge_account ${u.id}: ${e instanceof Error ? e.message : String(e)})`);
    }
    try {
      await sql(`delete from auth.users where id = ${literal(u.id)}`);
    } catch (e) {
      console.log(`  (cleanup: auth.users ${u.id}: ${e instanceof Error ? e.message : String(e)})`);
    }
  }
}

try {
  const inventory = await loadInventory(DATABASE_URL);
  const registryText = await Deno.readTextFile(new URL("./rls-expectations.json", import.meta.url));
  const parsed: unknown = JSON.parse(registryText);
  const registry = parseRegistry(parsed);

  const counts = { table: 0, view: 0, function: 0 };
  for (const r of inventory) counts[r.kind] += 1;
  console.log(
    `Inventory: ${counts.table} tables, ${counts.view} views, ${counts.function} security definer functions; registry: ${registry.tables.size} + ${registry.views.size} + ${registry.functions.size} entries`,
  );

  // --- Completeness: every live object registered, nothing stale ---------
  const live = { table: new Set<string>(), view: new Set<string>(), function: new Set<string>() };
  for (const r of inventory) {
    const k = objectKey(r);
    if (live[r.kind].has(k)) {
      check(`${k}: one ${r.kind} per name (overloads need a registry key with args)`, false);
    }
    live[r.kind].add(k);
  }
  const sections: Array<[keyof typeof live, Map<string, unknown>]> = [
    ["table", registry.tables],
    ["view", registry.views],
    ["function", registry.functions],
  ];
  for (const [kind, section] of sections) {
    for (const k of live[kind]) check(`${k}: registered`, section.has(k), `no ${kind} entry in rls-expectations.json`);
    for (const k of section.keys()) check(`${k}: still in the catalog`, live[kind].has(k), `stale ${kind} entry`);
  }

  // --- Catalog facts -------------------------------------------------------
  for (const r of inventory) {
    const k = objectKey(r);
    if (r.kind === "table") {
      check(`${k}: row level security enabled`, r.rlsEnabled === true);
      const e = registry.tables.get(k);
      if (e && r.policyCount === 0) {
        const all = [e.anon, e.authenticated].flatMap((o) => [o.select, o.insert, o.update, o.delete]);
        check(`${k}: no policy, so registered as deny-all`, all.every((a) => a === "none"));
      }
    } else if (r.kind === "view") {
      const e = registry.views.get(k);
      if (e) {
        check(`${k}: security_invoker is ${e.securityInvoker} as registered`, r.securityInvoker === e.securityInvoker);
      }
    } else {
      const e = registry.functions.get(k);
      if (e && r.returnsTrigger) check(`${k}: a trigger function is registered as trigger`, e.kind === "trigger", e.kind);
    }
  }

  if (failed > 0) {
    console.log("Registry incomplete or catalog drifted: probes skipped.");
  } else {
    // --- Probes ------------------------------------------------------------
    const a = await signUp("a");
    const b = await signUp("b");
    const c = await signUp("c");
    const d = await signUp("d");
    await seedWorld(a, b, c, d);

    const actors: Actor[] = [{ label: "anon", client: anon }, { label: "stranger", client: b.client }];
    const byKey = new Map(inventory.map((r) => [`${r.kind}:${objectKey(r)}`, r]));
    for (const [k, e] of registry.tables) await probeTable(k.replace(/^public\./, ""), e, actors);
    for (const [k, e] of registry.views) await probeView(k.replace(/^public\./, ""), e, actors);
    const tablesByName = new Map([...registry.tables].map(([k, e]) => [k.replace(/^public\./, ""), e]));
    for (const [k, e] of registry.functions) {
      const row = byKey.get(`function:${k}`);
      if (row) await probeFunction(k, row, e, b, tablesByName);
    }
    if (unwitnessed.length > 0) {
      console.log(`Unwitnessed (no seedable row; only the blanket read is probed): ${unwitnessed.join(", ")}`);
    }
  }
} catch (e) {
  check("harness ran to completion", false, e instanceof Error ? e.message : String(e));
} finally {
  await cleanup();
}

console.log(`\nSummary: passed=${passed} failed=${failed}`);
Deno.exit(failed > 0 ? 1 : 0);
