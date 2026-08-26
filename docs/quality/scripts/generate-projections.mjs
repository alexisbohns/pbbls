#!/usr/bin/env node
/**
 * Kritik projections: regenerate the human-readable criteria reference
 * (docs/quality/criteria/*.md) and the per-criterion issue skeletons
 * (docs/quality/templates/*.md) from the canonical library
 * (docs/quality/library/framework.json).
 *
 * The markdown is a projection of the data, never the source of truth:
 * edit framework.json, then re-run  `node docs/quality/scripts/generate-projections.mjs`.
 */
import { readFileSync, writeFileSync, mkdirSync, readdirSync, unlinkSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const fw = JSON.parse(readFileSync(join(root, "library/framework.json"), "utf8"));

const SEV = { S: "≤ half day", M: "≤ 2 days", L: "≤ 1 week / 2 surfaces", XL: "> 1 week / cross-surface" };
const LEVELS = ["0 · Absent", "1 · Ad-hoc", "2 · Defined", "3 · Managed", "4 · Verified"];

const critDir = join(root, "criteria");
const tplDir = join(root, "templates");
mkdirSync(critDir, { recursive: true });
mkdirSync(tplDir, { recursive: true });
for (const dir of [critDir, tplDir])
  for (const f of readdirSync(dir)) if (f.endsWith(".md")) unlinkSync(join(dir, f));

const slug = (s) => s.toLowerCase().replace(/[^a-z0-9]+/g, "-");
const refLine = (r) => {
  const label = r.anchor ? `${r.title} — ${r.anchor}` : r.title;
  return r.url ? `[${label}](${r.url})` : label;
};

let indexRows = [];
for (const dom of fw.domains) {
  const criteria = fw.criteria.filter((c) => c.domain === dom.code && !c.superseded_by);
  const file = `${slug(dom.code)}-${slug(dom.name)}.md`;
  const out = [];
  out.push(`# ${dom.code} — ${dom.name}`);
  out.push("");
  out.push(`> Generated from [\`library/framework.json\`](../library/framework.json) v${fw.version} — do not edit by hand.`);
  out.push("");
  out.push(dom.description);
  out.push("");
  for (const c of criteria) {
    indexRows.push({ dom, c, file });
    out.push(`---\n`);
    out.push(`## ${c.id} · ${c.name}`);
    out.push("");
    out.push(`**${c.question}**`);
    out.push("");
    out.push(`\`${c.subcategory}\` · applies to: ${c.applies_to.map((s) => `\`${s}\``).join(" ")} · default impact **${c.default_impact}/5** · weight **${c.weight}/3**`);
    out.push("");
    out.push(c.definition);
    out.push("");
    out.push(`*Why it matters:* ${c.rationale}`);
    out.push("");
    out.push(`### Maturity anchors\n`);
    out.push(`| Level | Anchor |\n| --- | --- |`);
    ["l0", "l1", "l2", "l3", "l4"].forEach((k, i) => out.push(`| **${LEVELS[i]}** | ${c.level_anchors[k]} |`));
    out.push("");
    out.push(`### Audit checklist\n`);
    for (const step of c.checklist) out.push(`- [ ] ${step}`);
    out.push("");
    if (c.signals?.length) {
      out.push(`### Monitoring signals\n`);
      for (const s of c.signals) out.push(`- ${s}`);
      out.push("");
    }
    out.push(`### References\n`);
    for (const r of c.references) out.push(`- ${refLine(r)}`);
    out.push("");
    out.push(`### Typical remediation\n\n${c.remediation}`);
    out.push("");
    out.push(`*Issue skeleton:* [\`templates/${c.id.toLowerCase()}.md\`](../templates/${c.id.toLowerCase()}.md)`);
    out.push("");
  }
  writeFileSync(join(critDir, file), out.join("\n"));
}

// criteria/index.md
const idx = [];
idx.push(`# Criteria catalog — Kritik v${fw.version}`);
idx.push("");
idx.push(`> Generated from [\`library/framework.json\`](../library/framework.json) — do not edit by hand.`);
idx.push("");
idx.push(`${indexRows.length} active criteria across ${fw.domains.length} domains. Columns are the five Pebbles surfaces; ● = applicable.`);
idx.push("");
idx.push(`| Id | Criterion | Sub | web | ios | android | admin | supabase | I | W |`);
idx.push(`| --- | --- | --- | :-: | :-: | :-: | :-: | :-: | :-: | :-: |`);
const dot = (c, s) => (c.applies_to.includes(s) ? "●" : "·");
for (const { c, file } of indexRows)
  idx.push(
    `| [${c.id}](./${file}#${slug(c.id)}--${slug(c.name)}) | ${c.name} | \`${c.subcategory}\` | ${dot(c, "web")} | ${dot(c, "ios")} | ${dot(c, "android")} | ${dot(c, "admin")} | ${dot(c, "supabase")} | ${c.default_impact} | ${c.weight} |`,
  );
writeFileSync(join(critDir, "index.md"), idx.join("\n"));

// templates/<id>.md — prefilled issue skeletons
for (const { dom, c } of indexRows) {
  const t = [];
  t.push(`<!-- Kritik issue skeleton for ${c.id} (${dom.name}) — generated from library/framework.json v${fw.version}.`);
  t.push(`     Fill every {placeholder}; title convention below; labels: ${c.issue.labels.join(", ")} + the surface label + milestone. -->`);
  t.push("");
  t.push(`Title: ${c.issue.title_template}`);
  t.push("");
  t.push(c.issue.body_skeleton);
  t.push("");
  t.push(`---`);
  t.push(`_Criterion: **${c.id} · ${c.name}** (\`${c.subcategory}\`) — see [criteria reference](../criteria/index.md)._`);
  t.push(`_Question: ${c.question}_`);
  t.push(`_References: ${c.references.map(refLine).join(" · ")}_`);
  writeFileSync(join(tplDir, `${c.id.toLowerCase()}.md`), t.join("\n"));
}

console.log(`generated: ${fw.domains.length} domain docs, index (${indexRows.length} criteria), ${indexRows.length} issue skeletons`);
