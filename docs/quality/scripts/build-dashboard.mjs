#!/usr/bin/env node
/**
 * Build the self-contained Kritik audit dashboard (audits/<id>/dashboard.html)
 * by injecting the audit data into scripts/dashboard.tmpl.html.
 * Usage: node docs/quality/scripts/build-dashboard.mjs 2026-08
 */
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const auditId = process.argv[2] ?? "2026-08";
const A = join(root, "audits", auditId);
const fw = JSON.parse(readFileSync(join(root, "library/framework.json"), "utf8"));
const mx = JSON.parse(readFileSync(join(A, "matrix.json"), "utf8"));
const fd = JSON.parse(readFileSync(join(A, "findings.json"), "utf8")).findings;
const sc = JSON.parse(readFileSync(join(A, "scores.json"), "utf8")).assessments;
const critname = Object.fromEntries(fw.criteria.map((c) => [c.id, c.name]));
const critdom = Object.fromEntries(fw.criteria.map((c) => [c.id, c.id.split("-")[0]]));
const data = {
  audit: auditId, commit: mx.commit, fwv: fw.version,
  domains: fw.domains.map((d) => ({ code: d.code, name: d.name })),
  surfaces: fw.profiles.pbbls.surfaces.map((s) => s.id),
  surface_titles: Object.fromEntries(fw.profiles.pbbls.surfaces.map((s) => [s.id, s.title])),
  matrix: mx.matrix, overall: mx.overall, counts: mx.finding_counts,
  n_criteria: fw.criteria.length, n_assess: sc.length, n_findings: fd.length,
  findings: fd.map((f) => ({
    id: f.id, cr: f.criterion_id, crn: critname[f.criterion_id] || "", dom: critdom[f.criterion_id] || "",
    s: f.surface, sev: f.severity, pri: f.priority, cost: f.cost, i: f.impact, l: f.likelihood, t: f.title,
    detail: (f.detail || "").slice(0, 600), ev: (f.evidence || "").slice(0, 500), rem: (f.remediation || "").slice(0, 400),
    vv: (f.verification || {}).verdict || "",
  })),
};
const tmpl = readFileSync(join(root, "scripts/dashboard.tmpl.html"), "utf8");
writeFileSync(join(A, "dashboard.html"), tmpl.replace("/*__DATA__*/", JSON.stringify(data)));
console.log("wrote", join(A, "dashboard.html"));
