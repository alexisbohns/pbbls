import { objectKey, parseCsv, parseInventory } from "./rls-inventory.ts";

function assertEquals(actual: unknown, expected: unknown, label: string) {
  const a = JSON.stringify(actual);
  const e = JSON.stringify(expected);
  if (a !== e) throw new Error(`${label}: expected ${e}, got ${a}`);
}

function assertThrows(fn: () => unknown, label: string) {
  try {
    fn();
  } catch {
    return;
  }
  throw new Error(`${label}: expected a throw`);
}

const HEADER =
  "kind,schema,name,identity_args,rls_enabled,rls_forced,policy_count,security_invoker,anon_select,authenticated_select,returns_trigger,anon_execute,authenticated_execute,service_role_execute";

Deno.test("parseCsv keeps commas, doubled quotes and newlines inside quoted fields", () => {
  assertEquals(
    parseCsv('a,"b, c","say ""hi""","x\ny"\r\nz,,\n'),
    [["a", "b, c", 'say "hi"', "x\ny"], ["z", "", ""]],
    "records",
  );
});

Deno.test("parseCsv rejects an unterminated quote", () => {
  assertThrows(() => parseCsv('a,"b\n'), "unterminated");
});

Deno.test("parseInventory types one row of each kind", () => {
  const csv = [
    HEADER,
    "table,public,pebbles,,t,f,4,,f,t,,,,",
    "view,public,v_moments_full,,,,,t,f,t,,,,",
    'function,public,admin_set_domain_glyph,"p_domain_id uuid, p_strokes jsonb, p_view_box text",,,,,,,f,f,t,t',
    "",
  ].join("\n");
  const rows = parseInventory(csv);
  assertEquals(rows.length, 3, "row count");
  assertEquals([rows[0].kind, rows[0].rlsEnabled, rows[0].policyCount, rows[0].anonSelect], ["table", true, 4, false], "table");
  assertEquals([rows[1].securityInvoker, rows[1].rlsEnabled], [true, null], "view");
  assertEquals(rows[2].identityArgs, "p_domain_id uuid, p_strokes jsonb, p_view_box text", "identity args");
  assertEquals([rows[2].returnsTrigger, rows[2].anonExecute, rows[2].authenticatedExecute], [false, false, true], "grants");
  assertEquals(objectKey(rows[2]), "public.admin_set_domain_glyph", "key");
});

Deno.test("parseInventory refuses a drifted header or a malformed flag", () => {
  assertThrows(() => parseInventory("kind,schema\ntable,public\n"), "header");
  assertThrows(() => parseInventory(`${HEADER}\ntable,public,x,,yes,f,0,,f,f,,,,\n`), "flag");
  assertThrows(() => parseInventory(`${HEADER}\nsequence,public,x,,,,,,,,,,,\n`), "kind");
});
