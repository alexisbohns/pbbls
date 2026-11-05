/**
 * The live catalog the RLS denial harness (#1523) checks the registry against.
 *
 * `scripts/rls-inventory.sql` prints it as one CSV; this module parses that CSV
 * into typed rows and runs the query through `psql`. Kept apart from the
 * harness so the parser has a `deno test` that needs no database
 * (`rls-inventory_test.ts`).
 */

export type ObjectKind = "table" | "view" | "function";

export type InventoryRow = {
  kind: ObjectKind;
  schema: string;
  name: string;
  /** `pg_get_function_identity_arguments`, empty for relations. */
  identityArgs: string;
  /** Tables only. */
  rlsEnabled: boolean | null;
  rlsForced: boolean | null;
  policyCount: number | null;
  /** Views only. */
  securityInvoker: boolean | null;
  /** Relations only. */
  anonSelect: boolean | null;
  authenticatedSelect: boolean | null;
  /** Functions only. */
  returnsTrigger: boolean | null;
  anonExecute: boolean | null;
  authenticatedExecute: boolean | null;
  serviceRoleExecute: boolean | null;
};

const HEADER = [
  "kind",
  "schema",
  "name",
  "identity_args",
  "rls_enabled",
  "rls_forced",
  "policy_count",
  "security_invoker",
  "anon_select",
  "authenticated_select",
  "returns_trigger",
  "anon_execute",
  "authenticated_execute",
  "service_role_execute",
] as const;

/** RFC 4180 records: quoted fields may hold commas, doubled quotes and newlines. */
export function parseCsv(text: string): string[][] {
  const records: string[][] = [];
  let record: string[] = [];
  let field = "";
  let quoted = false;
  let i = 0;
  while (i < text.length) {
    const ch = text[i];
    if (quoted) {
      if (ch === '"') {
        if (text[i + 1] === '"') {
          field += '"';
          i += 2;
          continue;
        }
        quoted = false;
        i += 1;
        continue;
      }
      field += ch;
      i += 1;
      continue;
    }
    if (ch === '"' && field === "") {
      quoted = true;
    } else if (ch === ",") {
      record.push(field);
      field = "";
    } else if (ch === "\n" || ch === "\r") {
      if (ch === "\r" && text[i + 1] === "\n") i += 1;
      record.push(field);
      records.push(record);
      record = [];
      field = "";
    } else {
      field += ch;
    }
    i += 1;
  }
  if (quoted) throw new Error("csv: unterminated quoted field");
  if (field !== "" || record.length > 0) {
    record.push(field);
    records.push(record);
  }
  return records;
}

function flag(value: string, column: string, line: number): boolean | null {
  if (value === "") return null;
  if (value === "t") return true;
  if (value === "f") return false;
  throw new Error(`inventory line ${line}: ${column} is '${value}', expected t, f or empty`);
}

function isKind(value: string): value is ObjectKind {
  return value === "table" || value === "view" || value === "function";
}

export function parseInventory(csv: string): InventoryRow[] {
  const records = parseCsv(csv);
  const header = records.shift();
  if (!header || header.join(",") !== HEADER.join(",")) {
    throw new Error(`inventory header mismatch: got '${header?.join(",") ?? ""}'`);
  }
  return records.map((r, index) => {
    const line = index + 2;
    if (r.length !== HEADER.length) {
      throw new Error(`inventory line ${line}: ${r.length} fields, expected ${HEADER.length}`);
    }
    const [kind, schema, name, identityArgs, rls, forced, policies, invoker, anonSel, authSel, trig, anonEx, authEx, srvEx] = r;
    if (!isKind(kind)) throw new Error(`inventory line ${line}: unknown kind '${kind}'`);
    if (policies !== "" && !/^\d+$/.test(policies)) {
      throw new Error(`inventory line ${line}: policy_count is '${policies}'`);
    }
    return {
      kind,
      schema,
      name,
      identityArgs,
      rlsEnabled: flag(rls, "rls_enabled", line),
      rlsForced: flag(forced, "rls_forced", line),
      policyCount: policies === "" ? null : Number(policies),
      securityInvoker: flag(invoker, "security_invoker", line),
      anonSelect: flag(anonSel, "anon_select", line),
      authenticatedSelect: flag(authSel, "authenticated_select", line),
      returnsTrigger: flag(trig, "returns_trigger", line),
      anonExecute: flag(anonEx, "anon_execute", line),
      authenticatedExecute: flag(authEx, "authenticated_execute", line),
      serviceRoleExecute: flag(srvEx, "service_role_execute", line),
    };
  });
}

/** Registry key of a catalog object: `public.pebbles`, `public.save_moment`. */
export function objectKey(row: Pick<InventoryRow, "schema" | "name">): string {
  return `${row.schema}.${row.name}`;
}

/** Runs `rls-inventory.sql` against `databaseUrl` and parses the result. */
export async function loadInventory(databaseUrl: string): Promise<InventoryRow[]> {
  const sqlPath = new URL("./rls-inventory.sql", import.meta.url).pathname;
  const out = await new Deno.Command("psql", {
    args: [databaseUrl, "-X", "-q", "-v", "ON_ERROR_STOP=1", "-f", sqlPath],
    stdout: "piped",
    stderr: "piped",
  }).output();
  if (!out.success) {
    throw new Error(`psql inventory failed: ${new TextDecoder().decode(out.stderr).trim()}`);
  }
  return parseInventory(new TextDecoder().decode(out.stdout));
}
