#!/usr/bin/env node
/**
 * Kritik roll-up: compute the comparative matrix for one audit from
 *   library/framework.json  (criteria weights, domain weights, scales)
 *   audits/<id>/scores.json (assessments: criterion x surface -> level 0-4)
 *   audits/<id>/findings.json (findings with impact/likelihood/cost/status)
 * per framework.md §4.5: domain score = Σ(level*weight)/Σ(4*weight)*100,
 * grades A>=85 B>=70 C>=55 D>=40 else E, caps: open Critical -> D, open High -> B.
 *
 * Usage: node docs/quality/scripts/compute-matrix.mjs 2026-08
 * Writes audits/<id>/matrix.json and prints the markdown matrix.
 */
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const auditId = process.argv[2] ?? "2026-08";
const fw = JSON.parse(readFileSync(join(root, "library/framework.json"), "utf8"));
const scores = JSON.parse(readFileSync(join(root, `audits/${auditId}/scores.json`), "utf8"));
const findingsDoc = JSON.parse(readFileSync(join(root, `audits/${auditId}/findings.json`), "utf8"));

const profile = fw.profiles.pbbls;
const surfaces = profile.surfaces.map((s) => s.id);
const critById = Object.fromEntries(fw.criteria.map((c) => [c.id, c]));

export const severityOf = (f) => {
  const s = f.impact * f.likelihood;
  return s >= 20 ? "critical" : s >= 12 ? "high" : s >= 6 ? "medium" : s >= 2 ? "low" : "info";
};
export const priorityOf = (f) => {
  const sev = severityOf(f);
  if (sev === "critical") return "P0";
  if (sev === "high") return f.cost === "S" ? "P0" : "P1";
  if (sev === "medium") return f.cost === "S" ? "P1" : "P2";
  if (sev === "low") return f.cost === "S" ? "P2" : "P3";
  return "P3";
};
const gradeOf = (score) => (score >= 85 ? "A" : score >= 70 ? "B" : score >= 55 ? "C" : score >= 40 ? "D" : "E");
const GRADE_ORDER = ["A", "B", "C", "D", "E"];
const minGrade = (a, b) => GRADE_ORDER[Math.max(GRADE_ORDER.indexOf(a), GRADE_ORDER.indexOf(b))];

const openFindings = findingsDoc.findings.filter((f) => (f.status ?? "open") === "open");

const matrix = {};
for (const dom of fw.domains) {
  matrix[dom.code] = {};
  for (const surf of surfaces) {
    const rows = scores.assessments.filter(
      (a) => a.surface === surf && critById[a.criterion_id]?.domain === dom.code,
    );
    if (rows.length === 0) {
      matrix[dom.code][surf] = null; // N/A
      continue;
    }
    let num = 0, den = 0;
    for (const a of rows) {
      const w = critById[a.criterion_id].weight;
      num += a.level * w;
      den += 4 * w;
    }
    const score = Math.round((num / den) * 100);
    let grade = gradeOf(score);
    const cellFindings = openFindings.filter(
      (f) => f.surface === surf && critById[f.criterion_id]?.domain === dom.code,
    );
    const worst = cellFindings.map(severityOf);
    let capped = null;
    if (worst.includes("critical")) { const g = minGrade(grade, fw.scales.caps.critical_open); if (g !== grade) capped = g; grade = g; }
    else if (worst.includes("high")) { const g = minGrade(grade, fw.scales.caps.high_open); if (g !== grade) capped = g; grade = g; }
    matrix[dom.code][surf] = {
      score, grade, capped: capped !== null, criteria: rows.length,
      findings: { critical: worst.filter((s) => s === "critical").length, high: worst.filter((s) => s === "high").length, medium: worst.filter((s) => s === "medium").length, low: worst.filter((s) => s === "low").length },
    };
  }
}

const overall = {};
for (const surf of surfaces) {
  let num = 0, den = 0;
  for (const dom of fw.domains) {
    const cell = matrix[dom.code][surf];
    if (!cell) continue;
    const w = profile.domain_weights[dom.code] ?? 1;
    num += cell.score * w;
    den += w;
  }
  overall[surf] = den ? Math.round(num / den) : null;
}

const counts = { critical: 0, high: 0, medium: 0, low: 0, info: 0 };
for (const f of openFindings) counts[severityOf(f)]++;

const out = { audit_id: auditId, commit: scores.commit, framework_version: fw.version, matrix, overall, finding_counts: counts };
writeFileSync(join(root, `audits/${auditId}/matrix.json`), JSON.stringify(out, null, 1));

// markdown projection
const cellMd = (cell) => (cell ? `${cell.score} (${cell.grade}${cell.capped ? "\\*" : ""})` : "—");
const lines = [];
lines.push(`| Domain | ${surfaces.join(" | ")} |`);
lines.push(`| --- | ${surfaces.map(() => "---").join(" | ")} |`);
for (const dom of fw.domains)
  lines.push(`| **${dom.code}** ${dom.name} | ${surfaces.map((s) => cellMd(matrix[dom.code][s])).join(" | ")} |`);
lines.push(`| **Overall (weighted)** | ${surfaces.map((s) => (overall[s] != null ? `**${overall[s]} (${gradeOf(overall[s])})**` : "—")).join(" | ")} |`);
console.log(lines.join("\n"));
console.log(`\nopen findings: ${JSON.stringify(counts)}  ->  audits/${auditId}/matrix.json`);
