#!/usr/bin/env python3
"""Build gallery/game.html: the two-round blind pick-three game of the everyday wave.

Usage: python3 -I game-build.py          (from anywhere; writes gallery/game.html)

Reads figures.json and hands/hNN/batch-*.svg, inlines each hand's symbols with ids
prefixed by the hand (h31-cat-v1, h31-cat-v1-active). Round 1 judges the default
drawings, round 2 the default + filled pair. Every candidate is shown as a vertical
stack of four tiles: Paper ink, Paper accent, Night ink, Night accent.
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent
HANDS = ["h31", "h32", "h33"]
FIGS = json.loads((ROOT / "figures.json").read_text(encoding="utf-8"))
SYMBOL = re.compile(r"<symbol\b.*?</symbol>", re.S)
SID = re.compile(r'(<symbol\b[^>]*?\bid=")([^"]+)(")')


def hand_symbols(hand):
    out, ids = [], set()
    for f in sorted((ROOT / "hands" / hand).glob("batch-*.svg")):
        for sym in SYMBOL.findall(f.read_text(encoding="utf-8")):
            m = SID.search(sym)
            if not m:
                continue
            ids.add(m.group(2))
            out.append(SID.sub(lambda m: f"{m.group(1)}{hand}-{m.group(2)}{m.group(3)}", sym, count=1))
    return out, ids


def main():
    blocks, cands = [], {f["name"]: [] for f in FIGS}
    for h in HANDS:
        syms, ids = hand_symbols(h)
        blocks.append(f'<svg xmlns="http://www.w3.org/2000/svg" class="sprite" aria-hidden="true" focusable="false">{"".join(syms)}</svg>')
        for f in FIGS:
            for v in range(1, 6):
                base = f"{f['name']}-v{v}"
                if base in ids and base + "-active" in ids:
                    cands[f["name"]].append(f"{h}.v{v}")
    figs = [{"n": f["name"], "m": f["meaning"]} for f in FIGS if cands[f["name"]]]
    data = {"hands": HANDS, "figs": figs, "cands": {k: v for k, v in cands.items() if v}}
    page = TEMPLATE.replace("<!--SPRITES-->", "\n".join(blocks), 1).replace(
        "/*__DATA__*/null", json.dumps(data, separators=(",", ":")), 1)
    out = ROOT / "gallery" / "game.html"
    out.parent.mkdir(exist_ok=True)
    out.write_text(page, encoding="utf-8")
    n = sum(len(v) for v in data["cands"].values())
    print(f"wrote {out} ({out.stat().st_size // 1024} KiB): {len(figs)} figures, {n} candidates, {len(figs) * 2} steps")


TEMPLATE = r'''<title>Everyday Icon Pick</title>
<meta name="claude:viewport-fit-sides" content="cover">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Ysabeau:ital,wght@0,1..1000;1,1..1000&family=Ysabeau+SC:wght@500;600&display=swap">
<style>
/* Layout: one sticky bar, then a grid of candidate columns; each column is four stacked tiles. */
:root{
  --ground:#ECE8DF;--stock:#FBF9F4;--ink:#1F1D1A;--ink-2:#686255;--rule:rgba(31,29,26,.16);
  --accent:#8E4955;--on-accent:#FFFFFF;--like:#2F6F6A;--off:#C9C4B8;
  --display:"Ysabeau",ui-serif,Georgia,serif;--label:"Ysabeau SC","Ysabeau",ui-serif,Georgia,serif;
  --body:"Ysabeau",system-ui,-apple-system,"Segoe UI",sans-serif;
}
@media (prefers-color-scheme:dark){:root:not([data-theme="light"]){
  --ground:#141416;--stock:#1E1D1B;--ink:#ECE6DA;--ink-2:#A59E91;--rule:rgba(236,230,218,.14);
  --accent:#FFB2BC;--on-accent:#131315;--like:#7FD1C7;--off:#45454B;color-scheme:dark}}
:root[data-theme="dark"]{
  --ground:#141416;--stock:#1E1D1B;--ink:#ECE6DA;--ink-2:#A59E91;--rule:rgba(236,230,218,.14);
  --accent:#FFB2BC;--on-accent:#131315;--like:#7FD1C7;--off:#45454B;color-scheme:dark}
*{box-sizing:border-box}
body{margin:0;background:var(--ground);color:var(--ink);font-family:var(--body);font-size:16px;line-height:1.45;overflow-x:hidden}
body::before{content:"";position:fixed;inset:0 0 auto;height:env(safe-area-inset-top,0px);z-index:10;background:var(--ground)}
.sprite{position:absolute;width:0;height:0;overflow:hidden}
.wrap{max-width:1320px;margin:0 auto;padding-left:max(16px,env(safe-area-inset-left,0px));padding-right:max(16px,env(safe-area-inset-right,0px));padding-block:0 48px}
.kick{font-family:var(--label);letter-spacing:.14em;font-weight:600;font-size:12px;color:var(--ink-2);text-transform:lowercase}
h1{font-family:var(--display);font-weight:300;font-size:clamp(30px,5vw,44px);line-height:1.05;letter-spacing:-.02em;margin:6px 0 12px;text-wrap:balance}
h2{font-family:var(--label);font-weight:600;font-size:13px;letter-spacing:.16em;color:var(--ink-2);margin:36px 0 10px;text-transform:lowercase}
p{max-width:64ch;margin:0 0 12px}
.muted{color:var(--ink-2)}
.intro{padding-block:32px 8px}
.intro ul{padding-left:20px;max-width:64ch;color:var(--ink-2)}
.btn{appearance:none;border:0;border-radius:12px;background:var(--accent);color:var(--on-accent);font:inherit;font-weight:600;padding:10px 18px;min-height:44px;cursor:pointer}
.btn:disabled{background:var(--off);color:var(--ink-2);cursor:not-allowed}
.btn.quiet{background:transparent;color:var(--ink);box-shadow:inset 0 0 0 1px var(--rule)}
.link{appearance:none;border:0;background:none;color:var(--ink);font:inherit;text-decoration:underline;text-underline-offset:3px;cursor:pointer;padding:8px 4px;min-height:44px}
.btn:focus-visible,.link:focus-visible{outline:2px solid var(--ink-2);outline-offset:2px}
/* bar */
.bar{position:sticky;top:env(safe-area-inset-top,0px);z-index:5;background:var(--ground);display:flex;flex-wrap:wrap;align-items:center;gap:8px 14px;padding-block:12px;border-bottom:1px solid var(--rule);margin-bottom:18px}
.bar .what{flex:1 1 260px;min-width:0}
.bar .fig{font-family:var(--display);font-size:24px;font-weight:420;line-height:1.1}
.bar .meaning{font-size:14px;color:var(--ink-2);font-style:italic}
.bar .acts{display:flex;align-items:center;gap:8px;margin-left:auto}
.count{font-size:13px;color:var(--ink-2);font-variant-numeric:tabular-nums;white-space:nowrap}
.meter{height:3px;background:var(--rule);border-radius:2px;flex-basis:100%;overflow:hidden}
.meter i{display:block;height:100%;background:var(--accent);width:0}
/* candidates */
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(104px,1fr));gap:20px 14px;padding:12px 4px 0}
.card{position:relative;min-width:0}
.pick{display:flex;flex-direction:column;width:100%;appearance:none;border:0;padding:0;margin:0;background:none;cursor:pointer;border-radius:14px;overflow:hidden;outline:3px solid transparent;outline-offset:3px;transition:opacity .12s}
.pick:focus-visible{outline-color:var(--ink-2)}
.card.on .pick{outline-color:var(--accent)}
.grid.full .card:not(.on) .pick{opacity:.45}
.tile{display:flex;align-items:center;justify-content:center;gap:10px;height:58px;color:var(--t)}
.tile svg{display:block;flex:none}
.tile .sm{opacity:1}
.p-ink{background:#FEF9EF;--t:#1D1B16}
.p-acc{background:#FEF9EF;--t:#8E4955;box-shadow:inset 0 1px 0 rgba(29,27,22,.08)}
.n-ink{background:#131315;--t:#E7E2D9}
.n-acc{background:#131315;--t:#FFB2BC;box-shadow:inset 0 1px 0 rgba(231,226,217,.08)}
.arrow{width:6px;height:1px;background:currentColor;opacity:.35}
.badge{position:absolute;top:-9px;left:-9px;z-index:2;width:26px;height:26px;border-radius:13px;background:var(--accent);color:var(--on-accent);font-weight:700;font-size:14px;display:none;align-items:center;justify-content:center;box-shadow:0 0 0 2px var(--ground);pointer-events:none}
.card.on .badge{display:flex}
.like{position:absolute;top:-14px;right:-14px;z-index:3;width:36px;height:36px;padding:0;border:0;background:none;cursor:pointer;display:flex;align-items:center;justify-content:center;color:var(--ink-2)}
.like svg{width:20px;height:20px;background:var(--ground);border-radius:10px;box-shadow:0 0 0 2px var(--ground)}
.like[aria-pressed="true"]{color:var(--like)}
.like:focus-visible{outline:2px solid var(--ink-2);outline-offset:-4px;border-radius:18px}
/* results */
.tablewrap{overflow-x:auto;border:1px solid var(--rule);border-radius:12px;background:var(--stock)}
table{border-collapse:collapse;width:100%;font-size:14px;font-variant-numeric:tabular-nums}
th,td{padding:7px 10px;text-align:left;border-bottom:1px solid var(--rule);white-space:nowrap;vertical-align:middle}
th{font-weight:600;color:var(--ink-2)}
tr:last-child td{border-bottom:0}
td.num,th.num{text-align:right}
.who{display:inline-flex;align-items:center;gap:6px}
.chips{display:flex;flex-wrap:wrap;gap:4px 8px;white-space:normal}
textarea{display:block;width:100%;min-height:200px;font:12px/1.4 ui-monospace,SFMono-Regular,Menlo,monospace;color:var(--ink);background:var(--stock);border:1px solid var(--rule);border-radius:12px;padding:10px;resize:vertical}
.row{display:flex;flex-wrap:wrap;gap:8px;align-items:center;margin:10px 0}
footer{margin-top:32px;padding-top:12px;border-top:1px solid var(--rule);display:flex;flex-wrap:wrap;align-items:center;gap:4px 12px;font-size:14px}
.sync{font-size:13px;color:var(--ink-2)}
@media (prefers-reduced-motion:reduce){.pick{transition:none}}
</style>
<svg class="sprite" aria-hidden="true" focusable="false"><symbol id="ui-heart" viewBox="0 0 24 24"><path d="M12 19.5C7 16 4 13 4 9.6 4 7.3 5.8 5.5 8 5.5c1.7 0 3.1 1 4 2.4.9-1.4 2.3-2.4 4-2.4 2.2 0 4 1.8 4 4.1 0 3.4-3 6.4-8 9.9Z" fill="var(--hf,none)" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round"/></symbol></svg>
<!--SPRITES-->
<div class="wrap">
  <section id="screen-start" class="intro" hidden>
    <div class="kick">the everyday wave · november 2026</div>
    <h1>Everyday Icon Pick</h1>
    <p>Each figure drawn fifteen ways by three unnamed hands. Every candidate stands as a column of four tiles: ink and accent on Paper, then ink and accent on Night. Pick your three best, in order.</p>
    <ul>
      <li id="intro-count"></li>
      <li>Round 1 judges the default drawing, at 28 and 18 pt. Round 2 judges the pair: the default beside its filled, active twin.</li>
      <li>Tap a column to rank it 1, 2, 3; tap it again to un-rank. Enter goes to the next figure.</li>
      <li>The heart likes a drawing, as many as you want, whether or not it ranks. Likes are counted apart from the ranking.</li>
      <li>Progress is saved after every step.</li>
    </ul>
    <div class="row"><button type="button" class="btn" id="start-btn">Start</button><span class="muted" id="resume-note" hidden></span></div>
  </section>

  <section id="screen-game" hidden>
    <div class="bar">
      <div class="what"><div class="kick" id="where"></div><div class="fig" id="fig"></div><div class="meaning" id="meaning"></div></div>
      <div class="acts">
        <span class="count" id="count"></span>
        <button type="button" class="link" id="back-step">Back</button>
        <button type="button" class="link" id="skip-btn">Skip</button>
        <button type="button" class="btn" id="next-btn" disabled>Next</button>
      </div>
      <div class="meter"><i id="meter"></i></div>
    </div>
    <div class="grid" id="grid"></div>
  </section>

  <section id="screen-results" hidden>
    <div class="kick">results</div>
    <h1>What won</h1>
    <p class="muted" id="summary"></p>
    <button type="button" class="btn quiet" id="resume-btn" hidden>Back to the game</button>
    <h2>Hands</h2>
    <div class="tablewrap"><table id="t-hands"></table></div>
    <h2>Figures</h2>
    <div class="tablewrap"><table id="t-figs"></table></div>
    <h2>Results JSON</h2>
    <div class="row"><button type="button" class="btn" id="copy-btn">Copy results</button><span class="muted" id="copy-status" role="status"></span></div>
    <textarea id="json" readonly spellcheck="false" aria-label="Results JSON"></textarea>
  </section>

  <footer>
    <button type="button" class="link" id="results-link">Results so far</button>
    <button type="button" class="link" id="reset-link">Reset</button>
    <span id="reset-confirm" hidden>Clear the whole session? <button type="button" class="btn" id="reset-yes">Yes, reset</button> <button type="button" class="btn quiet" id="reset-no">Cancel</button></span>
    <span class="sync" id="sync" role="status"></span>
  </footer>
</div>
<script>
"use strict";
const DATA = /*__DATA__*/null;
const KEY = "everyday-icon-pick-v1";
const FIGS = DATA.figs.map((f) => f.n);
const MEANING = Object.fromEntries(DATA.figs.map((f) => [f.n, f.m]));
const TOTAL = FIGS.length * 2;
const PTS = [3, 2, 1];
const $ = (id) => document.getElementById(id);
let state = null, db = null, uid = null, writing = Promise.resolve();

function mulberry32(a){return function(){a=(a+0x6D2B79F5)|0;let t=Math.imul(a^(a>>>15),1|a);t=(t+Math.imul(t^(t>>>7),61|t))^t;return((t^(t>>>14))>>>0)/4294967296;};}
function shuffle(arr,rnd){const a=arr.slice();for(let i=a.length-1;i>0;i--){const j=Math.floor(rnd()*(i+1));[a[i],a[j]]=[a[j],a[i]];}return a;}
function seed(){try{const u=new Uint32Array(1);crypto.getRandomValues(u);return u[0];}catch(e){return Math.floor(Math.random()*4294967296)>>>0;}}

function newSession(){
  const s = seed(), rnd = mulberry32(s), order = shuffle(FIGS, rnd), steps = [];
  [1, 2].forEach((round) => order.forEach((fig) => steps.push({round, fig, order: shuffle(DATA.cands[fig], rnd)})));
  return {v: 1, seed: s, created: new Date().toISOString(), steps, picks: steps.map(() => null), likes: steps.map(() => []), cur: 0, sel: []};
}
function valid(s){
  return s && s.v === 1 && Array.isArray(s.steps) && s.steps.length === TOTAL && Array.isArray(s.picks) &&
    Array.isArray(s.likes) && Number.isInteger(s.cur) && s.cur >= 0 && s.cur <= TOTAL && Array.isArray(s.sel) &&
    s.steps.every((st) => st && DATA.cands[st.fig] && st.order.length === DATA.cands[st.fig].length);
}
function saveLocal(){try{localStorage.setItem(KEY, JSON.stringify(state));}catch(e){}}
function loadLocal(){try{const s=JSON.parse(localStorage.getItem(KEY)||"null");return valid(s)?s:null;}catch(e){return null;}}
function save(remote){
  saveLocal();
  if (!remote || !db || !uid) return;
  const body = JSON.parse(JSON.stringify(state));
  body.updatedAt = new Date().toISOString();
  body.results = compute();
  writing = writing.then(() => db.doc("sessions/" + uid).set(body)).then(
    () => { $("sync").textContent = "Saved for Claude to read."; },
    (e) => { $("sync").textContent = e && e.code === "quota_exceeded" ? "Storage full: copy the results JSON instead." : "Saved on this device only."; });
}

/* ---------- drawing ---------- */
function use(cand, fig, active, size){
  const [h, v] = cand.split(".");
  return '<svg width="' + size + '" height="' + size + '" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><use href="#' + h + "-" + fig + "-" + v + (active ? "-active" : "") + '"/></svg>';
}
function tile(cls, cand, st){
  const inner = st.round === 1
    ? use(cand, st.fig, false, 28) + use(cand, st.fig, false, 18)
    : use(cand, st.fig, false, 26) + '<span class="arrow"></span>' + use(cand, st.fig, true, 26);
  return '<span class="tile ' + cls + '">' + inner + '</span>';
}
function show(name){["start","game","results"].forEach((n) => { $("screen-" + n).hidden = n !== name; });}
function renderStep(){
  const st = state.steps[state.cur];
  $("where").textContent = "round " + st.round + " of 2 · " + (st.round === 1 ? "the default drawing" : "default and filled") + " · figure " + (state.cur % FIGS.length + 1) + " of " + FIGS.length;
  $("fig").textContent = st.fig.replace(/-/g, " ");
  $("meaning").textContent = MEANING[st.fig];
  $("meter").style.width = (100 * state.cur / TOTAL) + "%";
  $("back-step").hidden = state.cur === 0;
  $("grid").innerHTML = st.order.map((c, i) =>
    '<div class="card" data-c="' + c + '"><button type="button" class="pick" aria-pressed="false" aria-label="Candidate ' + (i + 1) + '"><span class="badge"></span>' +
    tile("p-ink", c, st) + tile("p-acc", c, st) + tile("n-ink", c, st) + tile("n-acc", c, st) +
    '</button><button type="button" class="like" aria-pressed="false" aria-label="Like candidate ' + (i + 1) + '"><svg viewBox="0 0 24 24" aria-hidden="true"><use href="#ui-heart"/></svg></button></div>').join("");
  updateSel();
  show("game");
}
function updateSel(){
  const likes = state.likes[state.cur] || [];
  $("grid").querySelectorAll(".card").forEach((card) => {
    const c = card.dataset.c, r = state.sel.indexOf(c), pick = card.querySelector(".pick"), like = card.querySelector(".like");
    card.classList.toggle("on", r >= 0);
    pick.setAttribute("aria-pressed", r >= 0 ? "true" : "false");
    card.querySelector(".badge").textContent = r >= 0 ? String(r + 1) : "";
    const on = likes.includes(c);
    like.setAttribute("aria-pressed", on ? "true" : "false");
    like.style.setProperty("--hf", on ? "currentColor" : "none");
  });
  $("grid").classList.toggle("full", state.sel.length === 3);
  $("next-btn").disabled = state.sel.length !== 3;
  $("count").textContent = state.sel.length + " of 3 · " + likes.length + " liked · step " + (state.cur + 1) + "/" + TOTAL;
}
function togglePick(c){
  const i = state.sel.indexOf(c);
  if (i >= 0) state.sel.splice(i, 1); else if (state.sel.length < 3) state.sel.push(c); else return;
  saveLocal(); updateSel();
}
function toggleLike(c){
  const l = state.likes[state.cur] || (state.likes[state.cur] = []);
  const i = l.indexOf(c);
  if (i >= 0) l.splice(i, 1); else l.push(c);
  saveLocal(); updateSel();
}
function advance(pick){
  state.picks[state.cur] = pick;
  state.cur += 1;
  state.sel = state.cur < TOTAL && state.picks[state.cur] ? state.picks[state.cur].slice() : [];
  save(true);
  window.scrollTo(0, 0);
  if (state.cur >= TOTAL) renderResults(false); else renderStep();
}
function stepBack(){
  if (state.cur === 0) return;
  state.cur -= 1;
  state.sel = (state.picks[state.cur] || []).slice();
  saveLocal(); renderStep(); window.scrollTo(0, 0);
}

/* ---------- scoring ---------- */
function compute(){
  const hands = {}, figs = {};
  DATA.hands.forEach((h) => { hands[h] = {points: 0, r1: 0, r2: 0, firsts: 0, likes: 0}; });
  FIGS.forEach((f) => { figs[f] = {r1: null, r2: null, points: {}, likes: {}}; });
  let answered = 0, skipped = 0, likeCount = 0;
  state.steps.forEach((st, i) => {
    const p = state.picks[i], F = figs[st.fig];
    (state.likes[i] || []).forEach((c) => { likeCount++; F.likes[c] = (F.likes[c] || 0) + 1; hands[c.split(".")[0]].likes++; });
    if (p === null) return;
    if (!p.length) { skipped++; return; }
    answered++;
    F["r" + st.round] = p.slice();
    p.forEach((c, r) => {
      const h = hands[c.split(".")[0]];
      h.points += PTS[r]; h["r" + st.round] += PTS[r]; if (r === 0) h.firsts++;
      F.points[c] = (F.points[c] || 0) + PTS[r];
    });
  });
  const out = {};
  FIGS.forEach((f) => {
    const F = figs[f];
    const order = Object.keys(F.points).sort((a, b) => F.points[b] - F.points[a] || (F.likes[b] || 0) - (F.likes[a] || 0) || a.localeCompare(b));
    out[f] = {winner: order[0] || null, ranking: order.map((c) => ({cand: c, points: F.points[c], likes: F.likes[c] || 0})),
      round1: F.r1, round2: F.r2, liked: Object.keys(F.likes).sort()};
  });
  return {app: "everyday-icon-pick", version: 1, seed: state.seed, createdAt: state.created, exportedAt: new Date().toISOString(),
    totalSteps: TOTAL, answered, skipped, likes: likeCount, complete: state.cur >= TOTAL,
    scoring: {points: PTS, likes: "counted apart, never added to points", candidate: "hand.version, symbol <hand>-<figure>-<version>[-active]"},
    hands, figures: out};
}

/* ---------- results ---------- */
let last = null;
function who(c, fig){
  if (!c) return '<span class="muted">—</span>';
  return '<span class="who">' + use(c, fig, false, 20) + use(c, fig, true, 20) + c + '</span>';
}
function renderResults(canResume){
  const r = compute(); last = r;
  $("summary").textContent = r.answered + " of " + TOTAL + " steps ranked, " + r.skipped + " skipped, " + r.likes + " likes" + (r.complete ? "." : ". The game is not finished.");
  $("resume-btn").hidden = !canResume || r.complete;
  let h = '<thead><tr><th>Hand</th><th class="num">Points</th><th class="num">Round 1</th><th class="num">Round 2</th><th class="num">Firsts</th><th class="num">Likes</th></tr></thead><tbody>';
  DATA.hands.slice().sort((a, b) => r.hands[b].points - r.hands[a].points).forEach((k) => {
    const x = r.hands[k];
    h += '<tr><td>' + k + '</td><td class="num">' + x.points + '</td><td class="num">' + x.r1 + '</td><td class="num">' + x.r2 + '</td><td class="num">' + x.firsts + '</td><td class="num">' + x.likes + '</td></tr>';
  });
  $("t-hands").innerHTML = h + '</tbody>';
  let t = '<thead><tr><th>Figure</th><th>Winner</th><th>Round 1 first</th><th>Round 2 first</th><th>Liked</th></tr></thead><tbody>';
  FIGS.forEach((f) => {
    const x = r.figures[f];
    t += '<tr><td>' + f + '</td><td>' + who(x.winner, f) + '</td><td>' + who(x.round1 && x.round1[0], f) + '</td><td>' + who(x.round2 && x.round2[0], f) +
      '</td><td><span class="chips">' + (x.liked.length ? x.liked.map((c) => '<span>' + c + '</span>').join("") : '<span class="muted">—</span>') + '</span></td></tr>';
  });
  $("t-figs").innerHTML = t + '</tbody>';
  $("json").value = JSON.stringify(r, null, 2);
  $("copy-status").textContent = "";
  show("results");
}
function copy(){
  const text = JSON.stringify(last, null, 2), ta = $("json");
  const fallback = () => { ta.focus(); ta.select(); $("copy-status").textContent = "Clipboard blocked: the text is selected, copy it by hand."; };
  try { navigator.clipboard.writeText(text).then(() => { $("copy-status").textContent = "Results copied."; }, fallback); } catch (e) { fallback(); }
}

/* ---------- start ---------- */
function showStart(){
  if (!state) state = loadLocal();
  $("intro-count").textContent = TOTAL + " steps: " + FIGS.length + " figures in two rounds, up to " + Math.max(...FIGS.map((f) => DATA.cands[f].length)) + " candidates each.";
  const n = $("resume-note");
  if (state && state.cur < TOTAL) { $("start-btn").textContent = "Resume"; n.textContent = "Saved session: step " + (state.cur + 1) + " of " + TOTAL + "."; n.hidden = false; }
  else if (state) { $("start-btn").textContent = "See results"; n.textContent = "Your session is complete."; n.hidden = false; }
  else { $("start-btn").textContent = "Start"; n.hidden = true; }
  show("start");
}
$("start-btn").addEventListener("click", async () => {
  const b = $("start-btn");
  b.disabled = true;
  await remoteReady;
  b.disabled = false;
  if (!state) { state = newSession(); save(true); }
  if (state.cur >= TOTAL) renderResults(false); else renderStep();
});
$("grid").addEventListener("click", (e) => {
  const card = e.target.closest(".card"); if (!card) return;
  if (e.target.closest(".like")) toggleLike(card.dataset.c); else if (e.target.closest(".pick")) togglePick(card.dataset.c);
});
$("next-btn").addEventListener("click", () => { if (state.sel.length === 3) advance(state.sel.slice()); });
$("skip-btn").addEventListener("click", () => advance([]));
$("back-step").addEventListener("click", stepBack);
$("resume-btn").addEventListener("click", () => { renderStep(); window.scrollTo(0, 0); });
$("results-link").addEventListener("click", () => {
  if (!state) state = loadLocal();
  if (!state) return;
  renderResults(state.cur < TOTAL); window.scrollTo(0, 0);
});
$("copy-btn").addEventListener("click", () => { if (last) copy(); });
$("reset-link").addEventListener("click", () => { $("reset-confirm").hidden = false; $("reset-link").hidden = true; });
$("reset-no").addEventListener("click", () => { $("reset-confirm").hidden = true; $("reset-link").hidden = false; });
$("reset-yes").addEventListener("click", () => {
  try { localStorage.removeItem(KEY); } catch (e) {}
  state = null; last = null; $("reset-confirm").hidden = true; $("reset-link").hidden = false; showStart(); window.scrollTo(0, 0);
});
document.addEventListener("keydown", (e) => {
  if (e.key !== "Enter" || $("screen-game").hidden) return;
  if (e.target && (e.target.tagName === "TEXTAREA" || (e.target.closest && e.target.closest("footer")))) return;
  e.preventDefault(); if (state && state.sel.length === 3) advance(state.sel.slice());
});
showStart();
/* Shared save: one document per player, so Claude can read the picks back.
   The saved session wins over this device's copy when it is further along,
   and Start waits for it, so a reload never starts a fresh game over it. */
const remoteReady = (async () => {
  try {
    if (!window.claude || !window.claude.use) return;
    const [d, u] = await Promise.all([window.claude.use("db"), window.claude.use("user")]);
    if (!d || !u) return;
    const id = await u.id();
    if (!id) return;
    db = d; uid = id;
    $("sync").textContent = "Loading your saved session…";
    const snap = await db.doc("sessions/" + uid).get();
    if (snap.exists) {
      const s = JSON.parse(JSON.stringify(snap.data())); delete s.results; delete s.updatedAt;
      if (valid(s) && (!state || s.cur > state.cur)) {
        state = s; saveLocal();
        if (!$("screen-start").hidden) showStart();
      }
    }
    $("sync").textContent = "Signed in: picks are saved for Claude to read.";
  } catch (e) { /* stays local */ }
})();
</script>
'''

if __name__ == "__main__":
    main()
