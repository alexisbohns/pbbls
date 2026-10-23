/* gem-method.js — the emotions gem: pebble worth, family shares and the allocation of a FIXED 29-facet
   seven-fold brilliant. Reference implementation, dependency-free. In the browser load it after month-r3.js
   (it only needs the pebble objects, not M3); under node: const GEM = require("./gem-method.js").
   Pebble fields used: emotion (family key), intensity (1..3), valence ("high" | "neutral" | "low"). */
(function (root, factory) {
  const GEM = factory();
  if (typeof module !== "undefined" && module.exports) module.exports = GEM;
  if (root) root.GEM = GEM;
})(typeof window !== "undefined" ? window : null, function () {
  "use strict";

  /* ---------------------------------------------------------------- the wheel
     Headquarters, clockwise from the top. Position p sits at angle -90° + p·360/7. */
  const ORDER = ["joy", "pride", "anger", "fear", "shame", "sadness", "peace"];
  const N = ORDER.length, STEP = 2 * Math.PI / N, HALF = STEP / 2;

  /* ---------------------------------------------------------------- pebble worth
     worth = intensity × charge(valence). Bright and heavy pebbles are charged alike; an even pebble weighs half.
     "flat" is the baseline (intensity only, what the boards used so far), kept for comparison. */
  const CHARGE = {
    charge: { high: 1, neutral: 0.5, low: 1 },
    flat: { high: 1, neutral: 1, low: 1 }
  };
  const normMethod = m => {
    const o = typeof m === "string" ? { worth: m } : (m || {});
    return { worth: o.worth in CHARGE ? o.worth : "charge", crown: o.crown === "fixed" ? "fixed" : "rotate" };
  };
  function worth(p, method) {
    const m = normMethod(method), c = CHARGE[m.worth];
    const i = Math.max(1, Math.min(3, Number(p.intensity) || 1));
    const v = p.valence in c ? p.valence : "neutral";       // an unknown valence counts as even
    return i * c[v];
  }
  function worths(P, method) {
    const w = {}; ORDER.forEach(k => { w[k] = 0; });
    P.forEach(p => { if (p.emotion in w) w[p.emotion] += worth(p, method); });
    return w;
  }
  function shares(P, method) {
    const w = worths(P, method), tot = ORDER.reduce((a, k) => a + w[k], 0), s = {};
    ORDER.forEach(k => { s[k] = tot ? w[k] / tot : 0; });
    return s;
  }
  /* the main family: the greatest worth; a tie goes to the family with more pebbles, then to the earlier
     one in ORDER (so a dead heat tips the stone toward the pleasant side). Null only for an empty month. */
  function main(P, method) {
    const w = worths(P, method), n = {}; ORDER.forEach(k => { n[k] = 0; });
    P.forEach(p => { if (p.emotion in n) n[p.emotion] += 1; });
    let best = null;
    ORDER.forEach(k => { if (w[k] > 0 && (!best || w[k] > w[best] + 1e-9 || (Math.abs(w[k] - w[best]) <= 1e-9 && n[k] > n[best]))) best = k; });
    return best;
  }

  /* ---------------------------------------------------------------- the cut
     Same construction as boards4/brilliant: table corners at T, star points at RS on the bisectors, kite tips at
     reach[p] on the girdle. The reach profile is FIXED (decoration, never data); it only shapes the drawing.
     Allocation uses NOMINAL areas, those of the round stone (reach 80 everywhere), so an identical month always
     gives an identical allocation whatever outline the designers pick. */
  const SHAPE = { T: 35, RS: 53, R0: 80, reach: [88, 84, 79, 76, 76, 79, 84], arcSteps: 24 };
  const ang = p => -Math.PI / 2 + p * STEP;
  const at = (a, r) => [r * Math.cos(a), r * Math.sin(a)];
  const shoelace = pts => { let s = 0; for (let i = 0; i < pts.length; i++) { const a = pts[i], b = pts[(i + 1) % pts.length]; s += a[0] * b[1] - b[0] * a[1]; } return Math.abs(s) / 2; };

  function geometry(reach) {
    const { T, RS, arcSteps } = SHAPE, R = p => reach[((p % N) + N) % N];
    const Tc = [], Sk = [], Gt = [], Ge = [], Re = [];
    for (let p = 0; p < N; p++) {
      Tc.push(at(ang(p), T)); Sk.push(at(ang(p) + HALF, RS)); Gt.push(at(ang(p), R(p)));
      Re.push((R(p) + R(p + 1)) / 2); Ge.push(at(ang(p) + HALF, Re[p]));
    }
    /* the rim between two girdle points: polar steps, radius eased (as the board draws it) */
    const arc = (a0, r0, a1, r1) => { const o = []; for (let s = 1; s <= arcSteps; s++) { const t = s / arcSteps, e = (1 - Math.cos(t * Math.PI)) / 2; o.push(at(a0 + (a1 - a0) * t, r0 + (r1 - r0) * e)); } return o; };
    const facets = [];
    facets.push({ id: "table", kind: "table", home: null, homes: [], theta: null, points: Tc.slice() });
    for (let p = 0; p < N; p++) facets.push({ id: `star_${p}`, kind: "star", home: p, homes: [p, (p + 1) % N], theta: p + 0.5, points: [Tc[p], Tc[(p + 1) % N], Sk[p]] });
    for (let p = 0; p < N; p++) facets.push({ id: `kite_${p}`, kind: "kite", home: p, homes: [p], theta: p, points: [Tc[p], Sk[p], Gt[p], Sk[(p + N - 1) % N]] });
    for (let p = 0; p < N; p++) {
      const h = (p + N - 1) % N;
      /* girdle_{2p}: counter-clockwise of tip p (between kite p and star p-1); girdle_{2p+1}: clockwise of tip p */
      facets.push({ id: `girdle_${2 * p}`, kind: "girdle", home: p, homes: [p], theta: p - 0.25, points: [Gt[p], Sk[h], Ge[h]].concat(arc(ang(p) - HALF, Re[h], ang(p), R(p)).slice(0, -1)) });
      facets.push({ id: `girdle_${2 * p + 1}`, kind: "girdle", home: p, homes: [p], theta: p + 0.25, points: [Gt[p]].concat(arc(ang(p), R(p), ang(p) + HALF, Re[p])).concat([Sk[p]]) });
    }
    facets.forEach(f => { f.drawnArea = shoelace(f.points); });
    return facets;
  }
  const ROUND = geometry(new Array(N).fill(SHAPE.R0));
  const AREA = {};                                                   // nominal area per kind (the round stone)
  ROUND.forEach(f => { if (!(f.kind in AREA)) AREA[f.kind] = f.drawnArea; });
  const FACETS = geometry(SHAPE.reach).map(f => Object.assign(f, { area: AREA[f.kind] }));
  const BY_ID = {}; FACETS.forEach(f => { BY_ID[f.id] = f; });
  const IDS = FACETS.map(f => f.id);
  const ALL_AREA = IDS.reduce((a, id) => a + BY_ID[id].area, 0);

  /* adjacency for conquest: shared edges, plus the rim cycle (the two girdle facets that meet at a tip).
     The table bridges nothing: it is a flag, never a path. */
  const ADJ = {}; IDS.forEach(id => { ADJ[id] = []; });
  const link = (a, b) => { if (!ADJ[a].includes(b)) { ADJ[a].push(b); ADJ[b].push(a); } };
  for (let p = 0; p < N; p++) {
    const h = (p + N - 1) % N, n = (p + 1) % N;
    link(`kite_${p}`, `star_${h}`); link(`kite_${p}`, `star_${p}`);
    link(`kite_${p}`, `girdle_${2 * p}`); link(`kite_${p}`, `girdle_${2 * p + 1}`);
    link(`girdle_${2 * p + 1}`, `girdle_${2 * n}`);                // edge Sk[p]–Ge[p], across the valley
    link(`girdle_${2 * p}`, `girdle_${2 * p + 1}`);                // the tip Gt[p]: the rim is a cycle
    link(`star_${p}`, "table");                                    // listed for completeness; not walked
  }
  IDS.forEach(id => ADJ[id].sort());

  /* ---------------------------------------------------------------- allocation
     The 28 facets around the table form one cycle, clockwise from the top: kite_0, girdle_1, star_0, girdle_2,
     kite_1, … girdle_13, star_6, girdle_0. Laid end to end by nominal area they make a ring of length A.
     Every present family gets a wedge of that ring, share × A long; the main family's wedge is centred on the
     middle of kite_0 and the others follow clockwise in ORDER. A facet goes to the wedge that covers most of it
     (a dead heat goes to the earlier wedge in the walk, the counter-clockwise one). */
  const CYCLE = [];
  for (let p = 0; p < N; p++) CYCLE.push(`kite_${p}`, `girdle_${2 * p + 1}`, `star_${p}`, `girdle_${(2 * p + 2) % (2 * N)}`);
  const RING = (() => { let u = 0; return CYCLE.map(id => { const a = BY_ID[id].area, o = { id, u0: u, u1: u + a }; u += a; return o; }); })();
  const A = RING[RING.length - 1].u1;                               // = ALL_AREA − AREA.table
  const EPS = 1e-6 * A;
  const overlap = (a0, a1, b0, b1) => Math.max(0, Math.min(a1, b1) - Math.max(a0, b0));
  const cover = (f, w) => overlap(f.u0, f.u1, w.u0, w.u1) + overlap(f.u0, f.u1, w.u0 - A, w.u1 - A) + overlap(f.u0, f.u1, w.u0 + A, w.u1 + A);
  const wrap = u => ((u % A) + A) % A;

  function allocate(P, method) {
    const m = normMethod(method);
    const w = worths(P, method), s = shares(P, method), top = main(P, method);
    const owner = {}; IDS.forEach(id => { owner[id] = null; });
    const empty = { method: m, shares: s, worths: w, main: null, rotation: 0, walk: [], wedges: {}, facets: owner };
    if (!top) return empty;
    /* crown = "rotate" (the rule): the walk starts at the main family, whose wedge is centred on the top;
       "fixed" (comparison only): the walk starts at the leading edge of joy's seat, whoever leads. */
    const first = m.crown === "fixed" ? "joy" : top;
    const rotation = ORDER.indexOf(first);
    const walk = ORDER.map((_, i) => ORDER[(i + rotation) % N]).filter(k => w[k] > 0);
    /* wedges, in ring coordinates: the first family centred on kite_0's middle, then clockwise */
    const wedges = {};
    let u = m.crown === "fixed" ? AREA.kite / 2 - A / (2 * N) : AREA.kite / 2 - s[walk[0]] * A / 2;
    walk.forEach(k => { wedges[k] = { u0: u, u1: u + s[k] * A, target: s[k] * A }; u += s[k] * A; });
    /* each facet goes to the wedge covering most of it; a dead heat goes to the counter-clockwise wedge,
       the one that holds the facet's leading edge */
    const inside = (u, wd) => { const x = wrap(u - wd.u0); return x >= 0 && x < wd.u1 - wd.u0; };
    RING.forEach(f => {
      let best = null, bc = -1;
      walk.forEach(k => { const c = cover(f, wedges[k]); if (c > bc + EPS || (Math.abs(c - bc) <= EPS && inside(f.u0 + EPS, wedges[k]))) { best = k; bc = c; } });
      owner[f.id] = best;
    });
    /* minimum presence: a present family left with nothing takes the smallest facet its wedge touches
       (ties: the one it covers more, then the clockwise one), from an owner that keeps at least one facet */
    const count = k => IDS.filter(id => owner[id] === k).length;
    walk.forEach(k => {
      if (count(k)) return;
      const touched = RING.filter(f => cover(f, wedges[k]) > EPS && count(owner[f.id]) > 1)
        .sort((a, b) => (BY_ID[a.id].area - BY_ID[b.id].area) || (cover(b, wedges[k]) - cover(a, wedges[k])) || (CYCLE.indexOf(a.id) - CYCLE.indexOf(b.id)));
      if (touched.length) owner[touched[0].id] = k;
    });
    /* settle the boundaries: moving an end facet of a run to the neighbouring run is accepted when it cuts the
       total distance from target (first improving move along the cycle, repeat until none; a family is never
       emptied). Runs stay contiguous, the result is deterministic. */
    const held = {}; walk.forEach(k => { held[k] = 0; });
    RING.forEach(f => { held[owner[f.id]] += BY_ID[f.id].area; });
    const miss = k => Math.abs(held[k] - wedges[k].target);
    for (let guard = 0; guard < 64; guard++) {
      let moved = false;
      for (let i = 0; i < RING.length && !moved; i++) {
        const f = RING[i], a = f.id, k = owner[a], area = BY_ID[a].area;
        if (count(k) < 2) continue;
        for (const j of [i - 1, i + 1]) {
          const g = owner[RING[(j + RING.length) % RING.length].id];
          if (g === k) continue;
          const before = miss(k) + miss(g);
          const after = Math.abs(held[k] - area - wedges[k].target) + Math.abs(held[g] + area - wedges[g].target);
          if (after < before - EPS) { owner[a] = g; held[k] -= area; held[g] += area; moved = true; break; }
        }
      }
      if (!moved) break;
    }
    const out = { method: m, shares: s, worths: w, main: top, rotation, walk, wedges: {}, facets: owner };
    walk.forEach(k => {
      const wd = wedges[k], c = wrap((wd.u0 + wd.u1) / 2 - AREA.kite / 2);
      out.wedges[k] = { start: wrap(wd.u0 - AREA.kite / 2) / A, end: wrap(wd.u1 - AREA.kite / 2) / A, centre: c / A, target: wd.target, held: held[k] };
    });
    return out;
  }

  /* ---------------------------------------------------------------- summaries, for tables and tests */
  function summary(P, method) {
    const a = allocate(P, method);
    const rows = ORDER.map(k => {
      const ids = IDS.filter(id => a.facets[id] === k);
      const area = ids.reduce((t, id) => t + BY_ID[id].area, 0);
      const pts = [].concat(...ids.map(id => BY_ID[id].points)), n = pts.length || 1;
      const centroid = [pts.reduce((t, q) => t + q[0], 0) / n, pts.reduce((t, q) => t + q[1], 0) / n];
      return { family: k, worth: a.worths[k], share: a.shares[k], facets: ids, count: ids.length, area, areaShare: area / A, centroid, wedge: a.wedges[k] || null };
    });
    return { allocation: a, rows, neutral: IDS.filter(id => a.facets[id] === null) };
  }

  return { ORDER, N, SHAPE, AREA, FACETS, IDS, BY_ID, ADJ, CYCLE, RING, A, CHARGE, worth, worths, shares, main, allocate, summary, geometry };
});
