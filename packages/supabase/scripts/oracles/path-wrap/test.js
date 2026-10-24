/* node test.js — runs gem-method.js on the round-2 fixture, checks the invariants, prints the worked tables
   (markdown) that METHOD.md quotes. No dependencies. */
"use strict";
const path = require("path"), fs = require("fs");
const GEM = require(path.join(__dirname, "gem-method.js"));
const FIX = JSON.parse(fs.readFileSync(path.join(__dirname, "fixture-r2.json"), "utf8"));
const sortP = a => [...a].sort((x, y) => x.date - y.date || x.time.localeCompare(y.time));
const OCT = sortP(FIX.states.month_glimpse.pebbles);
const THIN = sortP([...FIX.states.few.pebbles, ...FIX.states.few_with_one_soul.pebbles]);
const QUIET = sortP(FIX.states.few.pebbles.filter(p => p.date === 13 || p.date === 15));
const HEAVY = OCT.filter(p => p.valence !== "high");
const PEACE = OCT.filter(p => p.emotion === "peace");
const EQUAL = GEM.ORDER.map(k => ({ emotion: k, intensity: 2, valence: "high", title: `equal ${k}` }));
const ONE = [OCT[7]];                                                     // Rooftop birthday, joy 3 high
const TIE2 = [{ emotion: "anger", intensity: 2, valence: "low" }, { emotion: "sadness", intensity: 2, valence: "low" }];
const SMALL = OCT.concat([{ emotion: "shame", intensity: 1, valence: "neutral", title: "one small even shame" }]);

const NAME = { joy: "joy", pride: "pride", anger: "anger", fear: "fear", shame: "shame", sadness: "sadness", peace: "peaceful" };
const pct = v => (v * 100).toFixed(1) + "%";
const f1 = v => Number.isInteger(v) ? String(v) : v.toFixed(1);
let failures = 0;
const check = (ok, msg) => { if (!ok) { failures++; console.error("FAIL  " + msg); } };

function invariants(P, label, method) {
  const a = GEM.allocate(P, method), present = GEM.ORDER.filter(k => a.worths[k] > 0);
  const ids = GEM.IDS.filter(id => id !== "table");
  check(a.facets.table === null, `${label}: table stays neutral`);
  if (!present.length) { check(ids.every(id => a.facets[id] === null), `${label}: empty month → no colour`); return a; }
  check(ids.every(id => present.includes(a.facets[id])), `${label}: every facet coloured by a present family`);
  present.forEach(k => check(ids.some(id => a.facets[id] === k), `${label}: ${k} holds at least one facet`));
  if (a.method.crown === "rotate") { check(a.walk[0] === a.main, `${label}: main family ${a.main} leads the walk`); check(a.facets.kite_0 === a.main, `${label}: main family holds kite_0 (top)`); }
  /* the clockwise order never changes: the walk is ORDER rotated, absent families skipped */
  const idx = a.walk.map(k => (GEM.ORDER.indexOf(k) - a.rotation + 7) % 7);
  check(idx.every((v, i) => i === 0 || v > idx[i - 1]), `${label}: walk keeps the clockwise order (${a.walk.join(" ")})`);
  /* area honesty: nobody is further than one kite (nominal) from its target */
  present.forEach(k => { const wd = a.wedges[k]; check(Math.abs(wd.held - wd.target) <= GEM.AREA.kite + 1e-6, `${label}: ${k} within one kite of target (${wd.held.toFixed(0)} vs ${wd.target.toFixed(0)})`); });
  /* each territory is one contiguous run of the cycle (the minimum-presence chip may split a run once) */
  present.forEach(k => {
    let runs = 0; GEM.CYCLE.forEach((id, i) => { if (a.facets[id] === k && a.facets[GEM.CYCLE[(i + 27) % 28]] !== k) runs++; });
    check(runs <= 1 || (runs === 2 && a.facets[GEM.CYCLE[0]] === k && a.facets[GEM.CYCLE[27]] === k), `${label}: ${k}'s territory is one run (${runs})`);
  });
  return a;
}

function table(P, label, method) {
  const S = GEM.summary(P, method), a = S.allocation;
  let out = `\n#### ${label} — ${P.length} pebble${P.length === 1 ? "" : "s"}, main: ${NAME[a.main]} (on top), walk: ${a.walk.map(k => NAME[k]).join(" · ")}\n\n`;
  out += `| family | worth | share | wedge (turns cw from top) | facets (n) | nominal area | area share | facets |\n|---|---|---|---|---|---|---|---|\n`;
  S.rows.forEach(r => {
    const wd = r.wedge ? `${r.wedge.start.toFixed(3)} → ${r.wedge.end.toFixed(3)}` : "—";
    out += `| ${NAME[r.family]} | ${f1(r.worth)} | ${r.worth ? pct(r.share) : "—"} | ${wd} | ${r.count} | ${r.area.toFixed(0)} | ${r.worth ? pct(r.areaShare) : "—"} | ${r.facets.join(" ") || "—"} |\n`;
  });
  out += `\nTable: neutral, carries the count (${P.length}).\n`;
  return out;
}

const SETS = [["M3.OCT · October", OCT], ["M3.THIN", THIN], ["M3.QUIET", QUIET], ["Heavy (October without its bright pebbles)", HEAVY], ["One family (October's peace pebbles)", PEACE],
  ["Equal (one intensity-2 bright pebble per family)", EQUAL], ["One pebble (Rooftop birthday)", ONE], ["Two families tied", TIE2], ["October + one small even shame pebble", SMALL]];

let md = "";
SETS.forEach(([label, P]) => { invariants(P, label, "charge"); invariants(P, label, "flat"); invariants(P, label, { crown: "fixed" }); md += table(P, label, "charge"); });
invariants([], "empty", "charge");

/* equal month: every family holds exactly kite + 2 girdles + 1 star */
{
  const S = GEM.summary(EQUAL, "charge");
  S.rows.forEach((r, p) => check(r.count === 4 && Math.abs(r.areaShare - 1 / 7) < 1e-9 && r.facets.join() === `star_${p},kite_${p},girdle_${2 * p},girdle_${2 * p + 1}`, `equal: ${r.family} holds its seat (${r.facets.join(" ")})`));
}
/* one family: everything but the table */
check(GEM.summary(PEACE, "charge").rows.find(r => r.family === "peace").count === 28, "one family takes all 28 facets");
/* worth table */
const W = [[1, "high"], [1, "neutral"], [1, "low"], [2, "high"], [2, "neutral"], [2, "low"], [3, "high"], [3, "neutral"], [3, "low"]].map(([i, v]) => `${i} ${v} → ${f1(GEM.worth({ intensity: i, valence: v }, "charge"))}`);
/* stability: drop each October pebble in turn, count facets that change */
const base = GEM.allocate(OCT, "charge").facets;
const flips = OCT.map((p, i) => { const a = GEM.allocate(OCT.filter((_, j) => j !== i), "charge"); return GEM.IDS.filter(id => a.facets[id] !== base[id]).length; });
/* how far each family lands from its target, over all sets */
const devs = SETS.map(([l, P]) => { const a = GEM.allocate(P, "charge"); return [l, Math.max(...a.walk.map(k => Math.abs(a.wedges[k].held - a.wedges[k].target)))]; });
/* a flat comparison for October */
const flat = GEM.summary(OCT, "flat").rows.map(r => `${NAME[r.family]} ${pct(r.share)} → ${r.count}`).join(", ");

if (process.argv.includes("--md")) {
  console.log(md);
  console.log("\nWorth table (charge): " + W.join(" · "));
  console.log("\nStability, October minus one pebble, facets changed per dropped pebble: " + flips.join(" ") + ` (max ${Math.max(...flips)}, mean ${(flips.reduce((a, b) => a + b, 0) / flips.length).toFixed(1)})`);
  console.log("\nOctober under flat (intensity only): " + flat);
  console.log("\nLargest miss per set (nominal area; girdle = 516, kite = 1035): " + devs.map(([l, d]) => `${l.split(" ")[0]} ${d.toFixed(0)}`).join(" · "));
} else {
  console.log(md);
  console.log(failures ? `${failures} failure(s)` : "all checks pass");
}
process.exitCode = failures ? 1 : 0;
