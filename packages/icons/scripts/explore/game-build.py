#!/usr/bin/env python3
"""Build gallery/game.html: a blind 'pick your top 3' rating game over the 20 icon hands.

Reads hands/hNN/sprite.svg, inlines each as a hidden <svg> block with every symbol id
prefixed by the hand id (h07-home), and writes a self-contained page. Reproducible:
the output depends only on the sprites and this script.
"""
import json
import re
import sys
from pathlib import Path

# Usage: python3 -I game-build.py <wave>   (writes <wave>/gallery/game.html)
ROOT = Path(sys.argv[1]).resolve()
HANDS = [f"h{i:02d}" for i in range(1, 21)]
# The sprites name their symbols by the brief's source id; the wave's library.json
# maps each chosen drawing back to it, and the package manifest (when a row exists)
# gives the label: the first clause of the icon's meaning.
_lib = json.loads((ROOT / "library.json").read_text(encoding="utf-8"))
BASE = list(dict.fromkeys(e["source"] for e in _lib if e["kind"] == "chosen"))
PAIRS = [e["source"] for e in _lib if e["kind"] == "chosen" and e["hasActive"]]
_manifest = ROOT.parent.parent / "icons.json"
_meanings = ({row["name"]: row["meaning"] for row in json.loads(_manifest.read_text(encoding="utf-8"))["icons"]}
             if _manifest.exists() else {})
_official = {e["source"]: e["name"] for e in _lib if e["kind"] == "chosen"}
LABELS = {src: re.split(r"[:,.;]", _meanings.get(_official.get(src, ""), src))[0].strip() or src for src in BASE}
_notes = json.loads((ROOT / "notes.json").read_text(encoding="utf-8")) if (ROOT / "notes.json").exists() else {}
NAMES = {h: (_notes.get(h, {}).get("name", h) if isinstance(_notes.get(h), dict) else h) for h in HANDS}

ROOT_OPEN = re.compile(r"^\s*<svg\b[^>]*>", re.S)
ROOT_CLOSE = re.compile(r"</svg>\s*$", re.S)
SYMBOL_ID = re.compile(r'(<symbol\b[^>]*?\bid=")([^"]+)(")')


def sprite_block(hand: str) -> str:
    src = (ROOT / "hands" / hand / "sprite.svg").read_text(encoding="utf-8")
    inner = ROOT_CLOSE.sub("", ROOT_OPEN.sub("", src, count=1), count=1)
    inner, n = SYMBOL_ID.subn(lambda m: f"{m.group(1)}{hand}-{m.group(2)}{m.group(3)}", inner)
    expected = set(BASE) | {f"{p}-active" for p in PAIRS}
    found = set(re.findall(rf'<symbol\b[^>]*?\bid="{hand}-([^"]+)"', inner))
    if n != len(expected) or found != expected:
        raise SystemExit(f"{hand}: expected {len(expected)} symbols {sorted(expected - found)} missing, got {n}")
    return (f'<svg xmlns="http://www.w3.org/2000/svg" class="sprite" data-hand="{hand}" '
            f'width="0" height="0" aria-hidden="true" focusable="false">{inner}</svg>')


TEMPLATE = r'''<title>Icon Pick Three</title>
<meta name="claude:viewport-fit-sides" content="cover">
<style>
:root{
  color-scheme:light;
  --bg:#F3F1EC;--fg:#1D1B16;--muted:#686357;--surface:#FFFFFF;--line:#DAD5CA;
  --accent:#8E4955;--accent-fg:#FFFFFF;--disabled:#C9C4B8;--special:#2F6F6A;
}
@media (prefers-color-scheme: dark){
  :root:not([data-theme="light"]){
    color-scheme:dark;
    --bg:#0E0E10;--fg:#ECE8E1;--muted:#A39C8F;--surface:#1B1B1E;--line:#34343A;
    --accent:#FFB2BC;--accent-fg:#131315;--disabled:#45454B;--special:#7FD1C7;
  }
}
:root[data-theme="dark"]{
  color-scheme:dark;
  --bg:#0E0E10;--fg:#ECE8E1;--muted:#A39C8F;--surface:#1B1B1E;--line:#34343A;
  --accent:#FFB2BC;--accent-fg:#131315;--disabled:#45454B;--special:#7FD1C7;
}
*{box-sizing:border-box}
html,body{margin:0}
body{background:var(--bg);color:var(--fg);font-family:system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;font-size:15px;line-height:1.4;-webkit-text-size-adjust:100%;overflow-x:hidden}
.sprite{position:absolute;width:0;height:0;overflow:hidden}
.wrap{max-width:1440px;margin:0 auto;padding-left:max(16px,env(safe-area-inset-left,0px));padding-right:max(16px,env(safe-area-inset-right,0px));padding-bottom:max(24px,env(safe-area-inset-bottom,0px))}
[hidden]{display:none!important}
h1{font-size:24px;margin:24px 0 8px}
h2{font-size:18px;margin:28px 0 8px}
p{margin:0 0 12px}
.muted{color:var(--muted)}
.btn{appearance:none;border:0;border-radius:10px;background:var(--accent);color:var(--accent-fg);font:inherit;font-weight:600;padding:10px 18px;cursor:pointer;min-height:44px}
.btn:disabled{background:var(--disabled);color:var(--muted);cursor:not-allowed}
.btn.secondary{background:transparent;color:var(--fg);box-shadow:inset 0 0 0 1px var(--line)}
.link{appearance:none;border:0;background:none;color:var(--fg);font:inherit;text-decoration:underline;text-underline-offset:3px;cursor:pointer;padding:8px 4px;min-height:44px}
.intro{max-width:620px;padding:8px 0 24px}
.intro ul{padding-left:20px;margin:0 0 16px}
/* game bar */
.bar{position:sticky;top:0;z-index:5;background:var(--bg);display:flex;flex-wrap:wrap;align-items:center;gap:8px 12px;padding:max(10px,env(safe-area-inset-top,0px)) 0 10px;border-bottom:1px solid var(--line);margin-bottom:16px}
.bar .prog{flex:1 1 220px;min-width:0}
.bar .prog strong{display:block;font-size:15px}
.bar .prog span{font-size:13px;color:var(--muted)}
.bar .actions{display:flex;align-items:center;gap:8px;margin-left:auto}
.count{font-size:13px;color:var(--muted);white-space:nowrap}
/* candidate grid */
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(min(300px,100%),1fr));gap:22px 20px;padding:10px 4px 0}
.card{position:relative;min-width:0}
.pick{position:relative;display:grid;width:100%;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));appearance:none;border:0;padding:0;margin:0;background:none;font:inherit;text-align:left;cursor:pointer;border-radius:16px;outline:3px solid transparent;outline-offset:3px;transition:opacity .12s}
.pick:focus-visible{outline-color:var(--muted)}
.card.on .pick{outline-color:var(--accent)}
.grid.full .card:not(.on) .pick{opacity:.5}
.spec{position:absolute;top:-12px;right:-12px;z-index:3;width:32px;height:32px;padding:0;margin:0;border:0;background:none;cursor:pointer;display:flex;align-items:center;justify-content:center;border-radius:16px}
.spec::before{content:"";width:14px;height:14px;border-radius:7px;background:var(--bg);box-shadow:inset 0 0 0 1.5px var(--muted),0 0 0 2px var(--bg)}
.spec[aria-pressed="true"]::before{background:var(--special);box-shadow:inset 0 0 0 1.5px var(--special),0 0 0 2px var(--bg)}
.spec:focus-visible{outline:2px solid var(--muted);outline-offset:-2px}
.badge{position:absolute;top:-10px;left:-10px;z-index:2;width:28px;height:28px;border-radius:14px;background:var(--accent);color:var(--accent-fg);font-weight:700;font-size:15px;display:none;align-items:center;justify-content:center;box-shadow:0 0 0 2px var(--bg)}
.card.on .badge{display:flex}
.chip{display:inline-block;margin:0 6px 2px 0;white-space:nowrap}
.half{display:block;min-width:0;padding:14px 12px;color:var(--ink)}
.half:first-of-type{border-radius:16px 0 0 16px}
.half:last-of-type{border-radius:0 16px 16px 0}
@container none{}
.paper{background:#FEF9EF;--ink:#1D1B16;--ink2:#7D7667;--acc:#8E4955;--on-acc:#FFFFFF;--grp:#FFFFFF;--sep:rgba(29,27,22,.1);--rail:rgba(29,27,22,.05)}
.night{background:#131315;--ink:#E7E2D9;--ink2:#989080;--acc:#FFB2BC;--on-acc:#131315;--grp:#1F1F22;--sep:rgba(231,226,217,.1);--rail:rgba(231,226,217,.06)}
.half svg{display:block;flex:none}
/* context A: column */
.rail{display:flex;flex-direction:column;align-items:center;gap:12px;width:max-content;margin:0 auto;padding:12px 14px;border-radius:24px;background:var(--rail)}
.rail .top{display:flex;gap:8px}
/* context B: list */
.list{display:block;border-radius:12px;background:var(--grp);overflow:hidden}
.row{display:flex;align-items:center;gap:8px;height:44px;padding:0 10px;font-size:13px;color:var(--ink)}
.row+.row{box-shadow:inset 0 1px 0 var(--sep)}
.row .ics{display:flex;gap:4px;flex:none}
.row .lbl{flex:1;min-width:0;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
/* context C: toolbar */
.pbtn{display:flex;align-items:center;gap:8px;height:44px;padding:0 14px;border-radius:22px;background:var(--acc);color:var(--on-acc);font-size:14px;font-weight:600;white-space:nowrap;overflow:hidden}
.pbtn .lbl{min-width:0;overflow:hidden;text-overflow:ellipsis}
.strip{display:flex;justify-content:space-between;align-items:center;margin-top:16px;padding:10px 4px 2px;box-shadow:inset 0 1px 0 var(--sep)}
/* results */
.tablewrap{overflow-x:auto;-webkit-overflow-scrolling:touch;border:1px solid var(--line);border-radius:10px;background:var(--surface)}
table{border-collapse:collapse;width:100%;font-size:13px;font-variant-numeric:tabular-nums}
th,td{padding:8px 10px;text-align:left;border-bottom:1px solid var(--line);white-space:nowrap}
th{font-weight:600;color:var(--muted)}
tr:last-child td{border-bottom:0}
td.num,th.num{text-align:right}
.who{display:inline-flex;align-items:center;gap:6px}
.who svg{color:var(--fg)}
textarea{display:block;width:100%;min-height:220px;font:12px/1.4 ui-monospace,SFMono-Regular,Menlo,monospace;color:var(--fg);background:var(--surface);border:1px solid var(--line);border-radius:10px;padding:10px;resize:vertical}
.copyrow{display:flex;flex-wrap:wrap;gap:8px;align-items:center;margin:10px 0}
footer{margin-top:32px;padding-top:12px;border-top:1px solid var(--line);display:flex;flex-wrap:wrap;align-items:center;gap:4px 12px;font-size:14px}
#reset-confirm{display:inline-flex;flex-wrap:wrap;align-items:center;gap:8px}
</style>
<!--SPRITES-->
<div class="wrap">
  <section id="screen-start" class="intro" hidden>
    <h1>Icon Pick Three</h1>
    <p>Twenty unnamed icon sets, one icon at a time. Each step shows the same icon drawn twenty ways, in a Paper and a Night fragment. Tap your favourite, then your second, then your third.</p>
    <ul class="muted">
      <li>69 steps: 23 icons in three settings (a column, a list, a toolbar).</li>
      <li>Tap a ranked card again to un-rank it. Enter goes to the next step.</li>
      <li>The small circle in a card's corner marks it <em>special</em>: you like the drawing, just not for this concept. Mark as many as you like; it never changes the ranks.</li>
      <li>Your progress is saved on this device after every tap.</li>
    </ul>
    <button type="button" class="btn" id="start-btn">Start</button>
    <p class="muted" id="resume-note" style="margin-top:10px" hidden></p>
  </section>

  <section id="screen-game" hidden>
    <div class="bar">
      <div class="prog"><strong id="progress"></strong><span id="hint"></span></div>
      <div class="actions">
        <span class="count" id="count"></span>
        <button type="button" class="link" id="skip-btn">Skip</button>
        <button type="button" class="btn" id="next-btn" disabled>Next</button>
      </div>
    </div>
    <div class="grid" id="grid"></div>
  </section>

  <section id="screen-results" hidden>
    <h1>Results</h1>
    <p class="muted" id="results-summary"></p>
    <button type="button" class="btn secondary" id="back-btn" hidden>Back to the game</button>
    <h2>Hands</h2>
    <div class="tablewrap"><table id="hands-table"></table></div>
    <h2>Icons</h2>
    <div class="tablewrap"><table id="icons-table"></table></div>
    <h2>Specials by icon</h2>
    <p class="muted" id="specials-note"></p>
    <div class="tablewrap"><table id="specials-table"></table></div>
    <h2>Results JSON</h2>
    <div class="copyrow">
      <button type="button" class="btn" id="copy-btn">Copy results</button>
      <button type="button" class="btn secondary" id="copy-compact-btn">Copy (one line)</button>
      <span class="muted" id="copy-status" role="status"></span>
    </div>
    <textarea id="results-json" readonly spellcheck="false"></textarea>
  </section>

  <footer>
    <button type="button" class="link" id="results-link">Results so far</button>
    <button type="button" class="link" id="reset-link">Reset</button>
    <span id="reset-confirm" hidden>
      <span>Clear the whole session?</span>
      <button type="button" class="btn" id="reset-yes">Yes, reset</button>
      <button type="button" class="btn secondary" id="reset-no">Cancel</button>
    </span>
  </footer>
</div>
<script>
"use strict";
const DATA = /*__DATA__*/;
const KEY = "icon-pick-three-v1";
const TOTAL = DATA.base.length * 3;
const CTX = {
  A: {name: "column", others: 5, hint: "A column of tab icons; the rated one sits on top."},
  B: {name: "list", others: 4, hint: "A grouped list; the rated icon leads the first row."},
  C: {name: "toolbar", others: 4, hint: "A primary button above a toolbar strip."}
};
const ICON_PTS = [2, 1.5, 0.5];
const HAND_PTS = [1, 0.75, 0.25];
const SPECIAL_ICON = 1;
const SPECIAL_HAND = 0.5;
const $ = (id) => document.getElementById(id);
let state = null;
let resultsReturn = false;

function mulberry32(a) {
  return function () {
    a = (a + 0x6D2B79F5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
function shuffle(arr, rnd) {
  const a = arr.slice();
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(rnd() * (i + 1));
    const t = a[i]; a[i] = a[j]; a[j] = t;
  }
  return a;
}
function newSeed() {
  try { const u = new Uint32Array(1); crypto.getRandomValues(u); return u[0]; }
  catch (e) { return Math.floor(Math.random() * 4294967296) >>> 0; }
}
function newSession() {
  const seed = newSeed();
  const rnd = mulberry32(seed);
  const order = shuffle(DATA.base, rnd);
  const steps = [];
  ["A", "B", "C"].forEach((ctx) => {
    order.forEach((icon) => {
      let cand = shuffle(DATA.hands, rnd);
      const prev = steps.length ? steps[steps.length - 1].order.join() : "";
      while (cand.join() === prev) cand = shuffle(DATA.hands, rnd);
      const others = shuffle(DATA.base.filter((b) => b !== icon), rnd).slice(0, CTX[ctx].others);
      steps.push({ctx, icon, order: cand, others});
    });
  });
  return {v: 1, seed, created: new Date().toISOString(), steps, picks: steps.map(() => null), specials: steps.map(() => null), cur: 0, sel: [], spec: []};
}
function valid(s) {
  return s && s.v === 1 && Array.isArray(s.steps) && s.steps.length === TOTAL &&
    Array.isArray(s.picks) && s.picks.length === TOTAL && Number.isInteger(s.cur) &&
    s.cur >= 0 && s.cur <= TOTAL && Array.isArray(s.sel) &&
    s.steps.every((st) => st && CTX[st.ctx] && DATA.base.includes(st.icon) &&
      Array.isArray(st.order) && st.order.length === DATA.hands.length && Array.isArray(st.others));
}
function save() {
  try { localStorage.setItem(KEY, JSON.stringify(state)); } catch (e) { /* storage unavailable */ }
}
function load() {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return null;
    const s = JSON.parse(raw);
    if (!valid(s)) return null;
    if (!Array.isArray(s.specials) || s.specials.length !== TOTAL) s.specials = s.steps.map(() => null);
    if (!Array.isArray(s.spec)) s.spec = [];
    return s;
  } catch (e) { return null; }
}
function clearSaved() {
  try { localStorage.removeItem(KEY); } catch (e) { /* storage unavailable */ }
}

/* ---------- rendering of candidates ---------- */
const isPair = (id) => DATA.pairs.includes(id);
function ic(h, id, size, color) {
  return '<svg width="' + size + '" height="' + size + '" viewBox="0 0 24 24" style="color:' + color +
    '" aria-hidden="true" focusable="false"><use href="#' + h + '-' + id + '"/></svg>';
}
const CHEVRON = '<svg width="8" height="12" viewBox="0 0 8 12" aria-hidden="true" focusable="false" style="color:var(--ink2)"><path d="M1.5 1.5 6 6l-4.5 4.5" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/></svg>';

function ctxA(h, st, night) {
  const c = night ? "var(--acc)" : "var(--ink)";
  let top = ic(h, st.icon, 26, c);
  if (isPair(st.icon)) top += ic(h, st.icon + "-active", 26, c);
  return '<span class="rail"><span class="top">' + top + '</span>' +
    st.others.map((o) => ic(h, o, 26, "var(--ink2)")).join("") + '</span>';
}
function ctxB(h, st) {
  let first = ic(h, st.icon, 24, "var(--ink)");
  if (isPair(st.icon)) first += ic(h, st.icon + "-active", 24, "var(--ink)");
  const row = (icons, label) => '<span class="row"><span class="ics">' + icons + '</span><span class="lbl">' +
    label + '</span>' + CHEVRON + '</span>';
  return '<span class="list">' + row(first, DATA.labels[st.icon]) +
    st.others.map((o) => row(ic(h, o, 24, "var(--ink2)"), DATA.labels[o])).join("") + '</span>';
}
function ctxC(h, st) {
  const btnIcon = isPair(st.icon) ? st.icon + "-active" : st.icon;
  return '<span class="pbtn">' + ic(h, btnIcon, 22, "var(--on-acc)") + '<span class="lbl">' +
    DATA.labels[st.icon] + '</span></span><span class="strip">' + ic(h, st.icon, 24, "var(--ink)") +
    st.others.map((o) => ic(h, o, 24, "var(--ink2)")).join("") + '</span>';
}
function fragment(h, st, night) {
  if (st.ctx === "A") return ctxA(h, st, night);
  if (st.ctx === "B") return ctxB(h, st);
  return ctxC(h, st);
}

function showScreen(name) {
  ["start", "game", "results"].forEach((n) => { $("screen-" + n).hidden = n !== name; });
}
function renderStep() {
  const st = state.steps[state.cur];
  $("progress").textContent = "Step " + (state.cur + 1) + " of " + TOTAL + " · Context " + st.ctx + " · " + st.icon;
  $("hint").textContent = CTX[st.ctx].hint + (isPair(st.icon) ? " Judge the pair: base and active." : "");
  let html = "";
  st.order.forEach((h, i) => {
    html += '<div class="card" data-h="' + h + '"><button type="button" class="pick" aria-pressed="false" aria-label="Candidate ' + (i + 1) +
      '"><span class="badge"></span><span class="half paper">' + fragment(h, st, false) +
      '</span><span class="half night">' + fragment(h, st, true) + '</span></button>' +
      '<button type="button" class="spec" aria-pressed="false" title="special" aria-label="Mark candidate ' + (i + 1) + ' special"></button></div>';
  });
  $("grid").innerHTML = html;
  updateSelection();
  showScreen("game");
}
function updateSelection() {
  const sel = state.sel;
  $("grid").querySelectorAll(".card").forEach((card) => {
    const r = sel.indexOf(card.dataset.h);
    const pick = card.querySelector(".pick");
    card.classList.toggle("on", r >= 0);
    pick.setAttribute("aria-pressed", r >= 0 ? "true" : "false");
    card.querySelector(".badge").textContent = r >= 0 ? String(r + 1) : "";
    const base = pick.getAttribute("aria-label").replace(/, ranked \d$/, "");
    pick.setAttribute("aria-label", r >= 0 ? base + ", ranked " + (r + 1) : base);
    card.querySelector(".spec").setAttribute("aria-pressed", state.spec.includes(card.dataset.h) ? "true" : "false");
  });
  $("grid").classList.toggle("full", sel.length === 3);
  $("next-btn").disabled = sel.length !== 3;
  $("count").textContent = sel.length + " of 3 picked";
}
function toggle(h) {
  const i = state.sel.indexOf(h);
  if (i >= 0) state.sel.splice(i, 1);
  else if (state.sel.length < 3) state.sel.push(h);
  else return;
  save();
  updateSelection();
}
function toggleSpecial(h) {
  const i = state.spec.indexOf(h);
  if (i >= 0) state.spec.splice(i, 1);
  else state.spec.push(h);
  save();
  updateSelection();
}
function advance(pick) {
  state.picks[state.cur] = pick;
  state.specials[state.cur] = state.spec.slice();
  state.cur += 1;
  state.sel = [];
  state.spec = [];
  save();
  window.scrollTo(0, 0);
  if (state.cur >= TOTAL) { resultsReturn = false; renderResults(); }
  else renderStep();
}

/* ---------- scoring ---------- */
function compute() {
  const hands = {};
  DATA.hands.forEach((h) => {
    hands[h] = {name: DATA.names[h], handPoints: 0, iconPoints: 0,
      perContext: {A: {handPoints: 0, iconPoints: 0}, B: {handPoints: 0, iconPoints: 0}, C: {handPoints: 0, iconPoints: 0}},
      places: {first: 0, second: 0, third: 0},
      special: {count: 0, handPoints: 0, iconPoints: 0, perContext: {A: 0, B: 0, C: 0}, icons: {}}};
  });
  const icons = {};
  DATA.base.forEach((id) => { icons[id] = {label: DATA.labels[id], points: {}, special: {}, winners: {A: null, B: null, C: null}, top3: {A: [], B: [], C: []}}; });
  let specialMarks = 0;
  const picks = [];
  let skipped = 0;
  state.picks.forEach((p, i) => {
    if (p === null) return;
    const st = state.steps[i];
    const sp = (state.specials[i] || []).filter((h) => hands[h]);
    picks.push({step: i + 1, context: st.ctx, contextName: CTX[st.ctx].name, icon: st.icon, ranked: p.slice(), special: sp.slice()});
    const ic = icons[st.icon];
    sp.forEach((h) => {
      const x = hands[h].special;
      specialMarks += 1;
      x.count += 1;
      x.handPoints += SPECIAL_HAND;
      x.iconPoints += SPECIAL_ICON;
      x.perContext[st.ctx] += 1;
      x.icons[st.icon] = (x.icons[st.icon] || 0) + 1;
      ic.special[h] = (ic.special[h] || 0) + SPECIAL_ICON;
    });
    if (!p.length) { skipped += 1; return; }
    ic.winners[st.ctx] = p[0];
    ic.top3[st.ctx] = p.slice();
    p.forEach((h, r) => {
      const hr = hands[h];
      if (!hr) return;
      hr.handPoints += HAND_PTS[r];
      hr.iconPoints += ICON_PTS[r];
      hr.perContext[st.ctx].handPoints += HAND_PTS[r];
      hr.perContext[st.ctx].iconPoints += ICON_PTS[r];
      hr.places[["first", "second", "third"][r]] += 1;
      ic.points[h] = (ic.points[h] || 0) + ICON_PTS[r];
    });
  });
  const ranking = DATA.hands.slice().sort((a, b) =>
    hands[b].handPoints - hands[a].handPoints || hands[b].iconPoints - hands[a].iconPoints ||
    hands[b].places.first - hands[a].places.first || a.localeCompare(b));
  const iconsOut = {};
  DATA.base.forEach((id) => {
    const ic = icons[id];
    const top = Object.keys(ic.points).sort((a, b) => ic.points[b] - ic.points[a] || a.localeCompare(b))
      .slice(0, 3).map((h) => ({hand: h, name: DATA.names[h], iconPoints: ic.points[h]}));
    const seen = {};
    ["A", "B", "C"].forEach((c) => ic.top3[c].forEach((h) => { seen[h] = (seen[h] || 0) + 1; }));
    const two = Object.keys(seen).filter((h) => seen[h] >= 2).sort();
    const three = two.filter((h) => seen[h] === 3);
    const specialHands = Object.keys(ic.special).sort((a, b) => ic.special[b] - ic.special[a] || a.localeCompare(b))
      .map((h) => ({hand: h, name: DATA.names[h], specialIconPoints: ic.special[h]}));
    iconsOut[id] = {label: ic.label, topHands: top, winners: ic.winners, top3ByContext: ic.top3, special: specialHands,
      consistency: {twoOrMoreContexts: two.length, allThreeContexts: three.length, hands: two}};
  });
  return {
    app: "icon-pick-three", version: 1, seed: state.seed, createdAt: state.created, exportedAt: new Date().toISOString(),
    totalSteps: TOTAL, answeredSteps: picks.length, skippedSteps: skipped, specialMarks: specialMarks, complete: state.cur >= TOTAL,
    scoring: {iconPoints: ICON_PTS, handPoints: HAND_PTS, special: {iconPoints: SPECIAL_ICON, handPoints: SPECIAL_HAND, note: "separate tally, never added to the main points"}},
    contexts: {A: "column", B: "list", C: "toolbar"},
    iconOrder: state.steps.slice(0, DATA.base.length).map((s) => s.icon),
    picks: picks,
    ranking: ranking.map((h, i) => ({rank: i + 1, hand: h, name: DATA.names[h], handPoints: hands[h].handPoints, iconPoints: hands[h].iconPoints, specialHandPoints: hands[h].special.handPoints})),
    hands: hands,
    icons: iconsOut
  };
}

/* ---------- results ---------- */
let lastResults = null;
const fmt = (n) => (Math.round(n * 100) / 100).toString();
function who(h, icon) {
  if (!h) return '<span class="muted">—</span>';
  return '<span class="who">' + (icon ? '<svg width="20" height="20" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><use href="#' + h + '-' + icon + '"/></svg>' : '') +
    h + ' · ' + DATA.names[h] + '</span>';
}
function renderResults() {
  const r = compute();
  lastResults = r;
  $("results-summary").textContent = r.answeredSteps + " of " + TOTAL + " steps answered (" + r.skippedSteps + " skipped)" +
    (r.complete ? "." : ". The game is not finished yet.");
  $("back-btn").hidden = r.complete || !resultsReturn;
  let h = '<thead><tr><th class="num">#</th><th>Hand</th><th>Name</th><th class="num">Hand pts</th><th class="num">Icon pts</th>' +
    '<th class="num">A column</th><th class="num">B list</th><th class="num">C toolbar</th><th class="num">1st</th><th class="num">2nd</th><th class="num">3rd</th><th class="num">Special</th></tr></thead><tbody>';
  r.ranking.forEach((row) => {
    const x = r.hands[row.hand];
    h += '<tr><td class="num">' + row.rank + '</td><td>' + row.hand + '</td><td>' + x.name + '</td><td class="num">' + fmt(x.handPoints) +
      '</td><td class="num">' + fmt(x.iconPoints) + '</td><td class="num">' + fmt(x.perContext.A.handPoints) + '</td><td class="num">' +
      fmt(x.perContext.B.handPoints) + '</td><td class="num">' + fmt(x.perContext.C.handPoints) + '</td><td class="num">' + x.places.first +
      '</td><td class="num">' + x.places.second + '</td><td class="num">' + x.places.third + '</td><td class="num">' + fmt(x.special.handPoints) +
      ' <span class="muted">(' + x.special.count + ')</span></td></tr>';
  });
  $("hands-table").innerHTML = h + '</tbody>';
  let t = '<thead><tr><th>Icon</th><th>Winner A column</th><th>Winner B list</th><th>Winner C toolbar</th><th>Top hand overall</th><th class="num">In top 3 in 2+ contexts</th></tr></thead><tbody>';
  r.iconOrder.forEach((id) => {
    const x = r.icons[id];
    const top = x.topHands[0];
    t += '<tr><td>' + id + '</td><td>' + who(x.winners.A, id) + '</td><td>' + who(x.winners.B, id) + '</td><td>' + who(x.winners.C, id) +
      '</td><td>' + (top ? who(top.hand, id) + ' <span class="muted">' + fmt(top.iconPoints) + '</span>' : who(null)) +
      '</td><td class="num">' + x.consistency.twoOrMoreContexts + (x.consistency.allThreeContexts ? ' <span class="muted">(' + x.consistency.allThreeContexts + ' in all 3)</span>' : '') + '</td></tr>';
  });
  $("icons-table").innerHTML = t + '</tbody>';
  $("specials-note").textContent = r.specialMarks + " special marks. Kept apart from the ranking: 1 point to the hand's icon, 0.5 to the hand.";
  let sp = '<thead><tr><th>Hand</th><th>Name</th><th class="num">Marks</th><th class="num">Special pts</th><th>Icons marked special (times)</th></tr></thead><tbody>';
  const byHand = DATA.hands.filter((h) => r.hands[h].special.count > 0)
    .sort((a, b) => r.hands[b].special.count - r.hands[a].special.count || a.localeCompare(b));
  byHand.forEach((h) => {
    const x = r.hands[h].special;
    const ids = Object.keys(x.icons).sort((a, b) => x.icons[b] - x.icons[a] || a.localeCompare(b));
    sp += '<tr><td>' + h + '</td><td>' + DATA.names[h] + '</td><td class="num">' + x.count + '</td><td class="num">' + fmt(x.handPoints) +
      '</td><td style="white-space:normal;min-width:220px">' + ids.map((id) => '<span class="chip">' + id + ' \u00d7' + x.icons[id] + '</span>').join("") + '</td></tr>';
  });
  if (!byHand.length) sp += '<tr><td colspan="5" class="muted">No special marks yet.</td></tr>';
  $("specials-table").innerHTML = sp + '</tbody>';
  $("results-json").value = JSON.stringify(r, null, 2);
  $("copy-status").textContent = "";
  showScreen("results");
}
function copyText(text, label) {
  const ta = $("results-json");
  const fallback = () => {
    ta.value = text;
    ta.focus();
    ta.select();
    try { ta.setSelectionRange(0, text.length); } catch (e) { /* ignore */ }
    $("copy-status").textContent = "Clipboard blocked: the text is selected, copy it by hand.";
  };
  try {
    if (!navigator.clipboard || !navigator.clipboard.writeText) { fallback(); return; }
    navigator.clipboard.writeText(text).then(() => { $("copy-status").textContent = label + " copied."; }, fallback);
  } catch (e) { fallback(); }
}

/* ---------- start / reset ---------- */
function showStart() {
  const s = load();
  state = s;
  if (s && s.cur < TOTAL) {
    $("start-btn").textContent = "Resume";
    $("resume-note").textContent = "Saved session: step " + (s.cur + 1) + " of " + TOTAL + ".";
    $("resume-note").hidden = false;
  } else if (s) {
    $("start-btn").textContent = "See results";
    $("resume-note").textContent = "Your saved session is complete.";
    $("resume-note").hidden = false;
  } else {
    $("start-btn").textContent = "Start";
    $("resume-note").hidden = true;
  }
  showScreen("start");
}

$("start-btn").addEventListener("click", () => {
  if (!state) { state = newSession(); save(); }
  if (state.cur >= TOTAL) { resultsReturn = false; renderResults(); }
  else renderStep();
});
$("grid").addEventListener("click", (e) => {
  const card = e.target.closest(".card");
  if (!card) return;
  if (e.target.closest(".spec")) toggleSpecial(card.dataset.h);
  else if (e.target.closest(".pick")) toggle(card.dataset.h);
});
$("next-btn").addEventListener("click", () => { if (state && state.sel.length === 3) advance(state.sel.slice()); });
$("skip-btn").addEventListener("click", () => { if (state) advance([]); });
$("back-btn").addEventListener("click", () => { if (state && state.cur < TOTAL) { renderStep(); window.scrollTo(0, 0); } });
$("results-link").addEventListener("click", () => {
  if (!state) state = load();
  if (!state) { state = null; showStart(); $("resume-note").textContent = "No picks yet."; $("resume-note").hidden = false; return; }
  resultsReturn = !$("screen-game").hidden || state.cur < TOTAL;
  renderResults();
  window.scrollTo(0, 0);
});
$("copy-btn").addEventListener("click", () => {
  if (!lastResults) return;
  copyText(JSON.stringify(lastResults, null, 2), "Results");
});
$("copy-compact-btn").addEventListener("click", () => {
  if (!lastResults) return;
  copyText(JSON.stringify(lastResults), "One-line results");
});
$("reset-link").addEventListener("click", () => { $("reset-confirm").hidden = false; $("reset-link").hidden = true; });
$("reset-no").addEventListener("click", () => { $("reset-confirm").hidden = true; $("reset-link").hidden = false; });
$("reset-yes").addEventListener("click", () => {
  clearSaved();
  state = null;
  lastResults = null;
  $("reset-confirm").hidden = true;
  $("reset-link").hidden = false;
  showStart();
  window.scrollTo(0, 0);
});
document.addEventListener("keydown", (e) => {
  if (e.key !== "Enter" || $("screen-game").hidden) return;
  const tag = e.target && e.target.tagName;
  if (tag === "TEXTAREA" || (e.target && e.target.closest && e.target.closest("footer"))) return;
  e.preventDefault();
  if (state && state.sel.length === 3) advance(state.sel.slice());
});
showStart();
</script>
'''


def main() -> None:
    data = {"hands": HANDS, "names": NAMES, "base": BASE, "pairs": PAIRS, "labels": LABELS}
    sprites = "\n".join(sprite_block(h) for h in HANDS)
    page = TEMPLATE.replace("<!--SPRITES-->", sprites, 1).replace(
        "/*__DATA__*/", json.dumps(data, separators=(",", ":")), 1)
    out = ROOT / "gallery" / "game.html"
    out.parent.mkdir(exist_ok=True)
    out.write_text(page, encoding="utf-8")
    print(f"wrote {out} ({out.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
