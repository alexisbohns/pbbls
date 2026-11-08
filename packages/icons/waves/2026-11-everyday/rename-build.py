#!/usr/bin/env python3
"""Build gallery/names.html: name every drawing that was picked or liked at least once.

Usage: python3 -I rename-build.py [results/results-2026-11-08.json]

The pool is, per figure, the union of round-1 picks, round-2 picks and likes, deduplicated.
Each row shows the default and the filled twin and takes one kebab-case name; names are
unique across the list and must not take a name already shipped in ../../src/. The
original figure name is the placeholder. Names are saved per viewer for Claude to read.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
RESULTS = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "results" / "results-2026-11-08.json"
FIGS = json.loads((ROOT / "figures.json").read_text(encoding="utf-8"))
SRC = ROOT.parent.parent / "src"
SYMBOL = re.compile(r'<symbol\b[^>]*?\bid="([^"]+)"[^>]*>.*?</symbol>', re.S)


def main():
    res = json.loads(RESULTS.read_text(encoding="utf-8"))
    syms = {}
    for hand in ("h31", "h32", "h33"):
        for f in sorted((ROOT / "hands" / hand).glob("batch-*.svg")):
            for m in SYMBOL.finditer(f.read_text(encoding="utf-8")):
                syms[f"{hand}-{m.group(1)}"] = m.group(0)
    rows, used = [], []
    for fig in FIGS:
        x = res["figures"].get(fig["name"])
        if not x:
            continue
        picked = set((x.get("round1") or []) + (x.get("round2") or []))
        liked = set(x.get("liked") or [])
        pool = sorted(picked | liked, key=lambda c: (c not in picked, c not in liked, c))
        for c in pool:
            hand, v = c.split(".")
            sid = f"{hand}-{fig['name']}-{v}"
            for s in (sid, sid + "-active"):
                if s not in syms:
                    raise SystemExit(f"missing symbol {s}")
                used.append(re.sub(r'\bid="[^"]+"', f'id="{s}"', syms[s], count=1))
            rows.append({"k": f"{fig['name']}/{c}", "f": fig["name"], "s": sid,
                         "t": ("picked" if c in picked else "") + (" liked" if c in liked else "")})
    shipped = sorted({p.name.split(".")[0] for p in SRC.glob("*.svg")})
    figs = [{"n": f["name"], "m": f["meaning"]} for f in FIGS if any(r["f"] == f["name"] for r in rows)]
    data = {"rows": rows, "figs": figs, "shipped": shipped}
    page = TEMPLATE.replace("<!--SPRITES-->", '<svg class="sprite" aria-hidden="true" focusable="false">' + "".join(used) + "</svg>", 1)
    page = page.replace("/*__DATA__*/null", json.dumps(data, separators=(",", ":")), 1)
    out = ROOT / "gallery" / "names.html"
    out.parent.mkdir(exist_ok=True)
    out.write_text(page, encoding="utf-8")
    print(f"wrote {out} ({out.stat().st_size // 1024} KiB): {len(rows)} drawings in {len(figs)} figures, {len(shipped)} shipped names reserved")


TEMPLATE = r'''<title>Everyday Icon Names</title>
<meta name="claude:viewport-fit-sides" content="cover">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Ysabeau:ital,wght@0,1..1000;1,1..1000&family=Ysabeau+SC:wght@500;600&display=swap">
<style>
/* Layout: a sticky progress bar, then one section per figure with a row per drawing: the pair, then its name. */
:root{
  --ground:#ECE8DF;--stock:#FBF9F4;--ink:#1F1D1A;--ink-2:#686255;--rule:rgba(31,29,26,.16);
  --accent:#8E4955;--on-accent:#FFFFFF;--ok:#2F6F6A;--bad:#B4500A;--field:#FFFFFF;
  --display:"Ysabeau",ui-serif,Georgia,serif;--label:"Ysabeau SC","Ysabeau",ui-serif,Georgia,serif;
  --body:"Ysabeau",system-ui,-apple-system,"Segoe UI",sans-serif;--mono:ui-monospace,SFMono-Regular,Menlo,monospace;
}
@media (prefers-color-scheme:dark){:root:not([data-theme="light"]){
  --ground:#141416;--stock:#1E1D1B;--ink:#ECE6DA;--ink-2:#A59E91;--rule:rgba(236,230,218,.14);
  --accent:#FFB2BC;--on-accent:#131315;--ok:#7FD1C7;--bad:#FFB074;--field:#141416;color-scheme:dark}}
:root[data-theme="dark"]{
  --ground:#141416;--stock:#1E1D1B;--ink:#ECE6DA;--ink-2:#A59E91;--rule:rgba(236,230,218,.14);
  --accent:#FFB2BC;--on-accent:#131315;--ok:#7FD1C7;--bad:#FFB074;--field:#141416;color-scheme:dark}
*{box-sizing:border-box}
body{margin:0;background:var(--ground);color:var(--ink);font-family:var(--body);font-size:16px;line-height:1.45;overflow-x:hidden}
body::before{content:"";position:fixed;inset:0 0 auto;height:env(safe-area-inset-top,0px);z-index:10;background:var(--ground)}
.sprite{position:absolute;width:0;height:0;overflow:hidden}
.wrap{max-width:980px;margin:0 auto;padding-left:max(16px,env(safe-area-inset-left,0px));padding-right:max(16px,env(safe-area-inset-right,0px));padding-block:28px 64px}
.kick{font-family:var(--label);letter-spacing:.14em;font-weight:600;font-size:12px;color:var(--ink-2);text-transform:lowercase}
h1{font-family:var(--display);font-weight:300;font-size:clamp(30px,5vw,44px);line-height:1.05;letter-spacing:-.02em;margin:6px 0 10px;text-wrap:balance}
.lede{max-width:64ch;margin:0 0 6px;color:var(--ink-2)}
.bar{position:sticky;top:env(safe-area-inset-top,0px);z-index:5;background:var(--ground);display:flex;flex-wrap:wrap;align-items:center;gap:8px 14px;padding-block:12px;border-bottom:1px solid var(--rule);margin:14px 0 8px}
.tally{flex:1 1 240px;min-width:0;font-variant-numeric:tabular-nums;font-size:14px}
.tally strong{font-family:var(--display);font-size:22px;font-weight:420;margin-right:6px}
.tally .bad{color:var(--bad)}
.meter{flex-basis:100%;height:3px;background:var(--rule);border-radius:2px;overflow:hidden}
.meter i{display:block;height:100%;width:0;background:var(--ok)}
.tools{display:flex;flex-wrap:wrap;gap:8px;align-items:center}
select,input[type=search]{font:inherit;font-size:14px;color:var(--ink);background:var(--field);border:1px solid var(--rule);border-radius:10px;padding:8px 10px;min-height:40px}
input[type=search]{width:min(220px,100%)}
.btn{appearance:none;border:0;border-radius:10px;background:var(--accent);color:var(--on-accent);font:inherit;font-size:14px;font-weight:600;padding:8px 14px;min-height:40px;cursor:pointer}
.btn.quiet{background:transparent;color:var(--ink);box-shadow:inset 0 0 0 1px var(--rule)}
.btn:focus-visible,select:focus-visible{outline:2px solid var(--ink-2);outline-offset:2px}
section.fig{padding-top:18px}
section.fig h2{display:flex;flex-wrap:wrap;align-items:baseline;gap:4px 12px;margin:0 0 8px;font-family:var(--display);font-weight:420;font-size:22px;line-height:1.2}
section.fig h2 .m{font-family:var(--body);font-size:14px;font-style:italic;color:var(--ink-2);font-weight:400}
.rows{display:grid;grid-template-columns:repeat(auto-fill,minmax(min(290px,100%),1fr));gap:8px}
.row{display:flex;align-items:center;gap:12px;background:var(--stock);border:1px solid var(--rule);border-radius:12px;padding:8px 10px;min-width:0}
.pair{display:flex;gap:6px;flex:none;color:var(--ink)}
.pair svg{display:block;width:32px;height:32px}
.field{flex:1;min-width:0;display:flex;flex-direction:column;gap:2px}
.field input{width:100%;font:15px/1.3 var(--mono);color:var(--ink);background:var(--field);border:1px solid var(--rule);border-radius:8px;padding:7px 9px;min-height:38px}
.field input::placeholder{color:var(--ink-2);opacity:.7}
.field input:focus-visible{outline:2px solid var(--ink-2);outline-offset:1px}
.row.ok .field input{border-color:var(--ok)}
.row.tbr .pair{opacity:.35}
.row.tbr .field input{text-decoration:line-through;color:var(--ink-2)}
.row.tbr .meta .msg{color:var(--ink-2)}
.row.err .field input{border-color:var(--bad);border-width:2px;padding:6px 8px}
.meta{display:flex;justify-content:space-between;gap:8px;font-size:12px;color:var(--ink-2);min-height:16px}
.meta .msg{color:var(--bad)}
.tag{font-family:var(--label);letter-spacing:.08em;font-size:11px;text-transform:lowercase}
textarea{display:block;width:100%;min-height:180px;margin-top:10px;font:12px/1.4 var(--mono);color:var(--ink);background:var(--stock);border:1px solid var(--rule);border-radius:12px;padding:10px;resize:vertical}
.out{margin-top:36px;padding-top:12px;border-top:1px solid var(--rule)}
.out h2{font-family:var(--label);font-weight:600;font-size:13px;letter-spacing:.16em;color:var(--ink-2);text-transform:lowercase;margin:0 0 8px}
.status{font-size:13px;color:var(--ink-2)}
.empty{padding:28px 0;color:var(--ink-2)}
</style>
<!--SPRITES-->
<div class="wrap">
  <div class="kick">the everyday wave · names</div>
  <h1>Everyday Icon Names</h1>
  <p class="lede" id="lede"></p>
  <p class="lede">Names are lowercase words joined by hyphens, unique across the list, and never a name the set already ships. The grey text is the figure's original name: press Enter in an empty field to take it, if it is still free. Name a drawing <code>tbr</code> to remove it; any number of drawings can be tbr.</p>
  <div class="bar">
    <div class="tally" id="tally"></div>
    <div class="tools">
      <label class="sr" for="show" hidden>Show</label>
      <select id="show" aria-label="Show">
        <option value="all">All drawings</option>
        <option value="todo">Not named yet</option>
        <option value="err">With a problem</option>
        <option value="tbr">To be removed</option>
      </select>
      <input type="search" id="q" placeholder="Filter by figure or name" aria-label="Filter by figure or name">
    </div>
    <div class="meter"><i id="meter"></i></div>
  </div>
  <div id="list"></div>
  <div class="out">
    <h2>names json</h2>
    <div class="tools"><button type="button" class="btn" id="copy">Copy names</button><span class="status" id="copy-status" role="status"></span></div>
    <textarea id="json" readonly spellcheck="false" aria-label="Names JSON"></textarea>
    <p class="status" id="sync" role="status"></p>
  </div>
</div>
<script>
"use strict";
const DATA = /*__DATA__*/null;
const KEY = "everyday-icon-names-v1";
const SHIPPED = new Set(DATA.shipped);
const MEANING = Object.fromEntries(DATA.figs.map((f) => [f.n, f.m]));
const KEBAB = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
const TBR = "tbr";
const $ = (id) => document.getElementById(id);
let names = {}, db = null, uid = null, timer = null, writing = Promise.resolve();

function loadLocal(){try{const v=JSON.parse(localStorage.getItem(KEY)||"{}");return v&&typeof v==="object"?v:{};}catch(e){return {};}}
function saveLocal(){try{localStorage.setItem(KEY,JSON.stringify(names));}catch(e){}}
const clean = (v) => String(v || "").trim();
function count(m){return Object.values(m).filter((v) => clean(v)).length;}

/* ---------- validation ---------- */
function check(){
  const by = {};
  DATA.rows.forEach((r) => { const v = clean(names[r.k]); if (v && v.toLowerCase() !== TBR) (by[v] = by[v] || []).push(r.k); });
  const out = {};
  DATA.rows.forEach((r) => {
    const v = clean(names[r.k]);
    if (!v) { out[r.k] = {state: "todo", msg: ""}; return; }
    if (v.toLowerCase() === TBR) { out[r.k] = {state: "tbr", msg: ""}; return; }
    if (!KEBAB.test(v)) { out[r.k] = {state: "err", msg: "Use lowercase letters, digits and single hyphens."}; return; }
    if (SHIPPED.has(v)) { out[r.k] = {state: "err", msg: "The set already ships an icon named " + v + "."}; return; }
    if (by[v].length > 1) { out[r.k] = {state: "err", msg: "Also used by " + (by[v].length - 1) + " other drawing" + (by[v].length > 2 ? "s" : "") + "."}; return; }
    out[r.k] = {state: "ok", msg: ""};
  });
  return out;
}

/* ---------- render ---------- */
function use(sid){return '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><use href="#' + sid + '"/></svg>';}
function esc(s){return String(s).replace(/[&<>"]/g,(c)=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;"}[c]));}
function build(){
  $("lede").textContent = DATA.rows.length + " drawings from " + DATA.figs.length + " figures: everything you picked or liked at least once in the game, each once. Every one needs a name before it can join the set.";
  let html = "";
  DATA.figs.forEach((f) => {
    const rows = DATA.rows.filter((r) => r.f === f.n);
    html += '<section class="fig" data-f="' + f.n + '"><h2>' + esc(f.n) + '<span class="m">' + esc(MEANING[f.n]) + '</span></h2><div class="rows">' +
      rows.map((r, i) => {
        const id = "n-" + r.k.replace(/[^a-z0-9]/g, "-");
        return '<div class="row" data-k="' + r.k + '"><span class="pair">' + use(r.s) + use(r.s + "-active") + '</span>' +
          '<span class="field"><input id="' + id + '" type="text" autocomplete="off" autocapitalize="off" spellcheck="false" placeholder="' + esc(f.n) + '" aria-label="Name for ' + esc(f.n) + ' drawing ' + (i + 1) + '" value="' + esc(names[r.k] || "") + '">' +
          '<span class="meta"><span class="msg"></span><span class="tag">' + esc(r.t.trim()) + '</span></span></span></div>';
      }).join("") + '</div></section>';
  });
  $("list").innerHTML = html + '<p class="empty" id="empty" hidden>Nothing to show with this filter.</p>';
}
function refresh(){
  const res = check();
  let ok = 0, err = 0, tbr = 0;
  document.querySelectorAll(".row").forEach((el) => {
    const s = res[el.dataset.k];
    el.classList.toggle("ok", s.state === "ok");
    el.classList.toggle("err", s.state === "err");
    el.classList.toggle("tbr", s.state === "tbr");
    el.querySelector(".msg").textContent = s.state === "tbr" ? "To be removed." : s.msg;
    if (s.state === "ok") ok++; else if (s.state === "err") err++; else if (s.state === "tbr") tbr++;
  });
  const total = DATA.rows.length, done = ok + tbr, todo = total - done - err;
  $("tally").innerHTML = '<strong>' + done + ' of ' + total + '</strong> done · ' + ok + ' named · ' + tbr + ' to be removed' + (todo ? ' · ' + todo + ' to go' : '') +
    (err ? ' · <span class="bad">' + err + ' with a problem</span>' : '') + (done === total ? ' · all done' : '');
  $("meter").style.width = (100 * done / total) + "%";
  $("json").value = JSON.stringify({app: "everyday-icon-names", version: 1, exportedAt: new Date().toISOString(), named: ok, total,
    names: DATA.rows.filter((r) => clean(names[r.k])).map((r) => {
      const [hand, v] = r.k.split("/")[1].split(".");
      const s = res[r.k].state;
      return {figure: r.f, candidate: hand + "." + v, symbol: r.s, name: s === "tbr" ? null : clean(names[r.k]), remove: s === "tbr", ok: s === "ok" || s === "tbr"};
    })}, null, 1);
  filter(res);
}
function filter(res){
  res = res || check();
  const show = $("show").value, q = clean($("q").value).toLowerCase();
  let any = false;
  document.querySelectorAll("section.fig").forEach((sec) => {
    let n = 0;
    sec.querySelectorAll(".row").forEach((el) => {
      const k = el.dataset.k, s = res[k].state, v = clean(names[k]).toLowerCase();
      const hit = (show === "all" || s === show) && (!q || sec.dataset.f.includes(q) || v.includes(q));
      el.hidden = !hit; if (hit) n++;
    });
    sec.hidden = n === 0; if (n) any = true;
  });
  $("empty").hidden = any;
}

/* ---------- saving ---------- */
function queueRemote(){
  if (!db || !uid) return;
  clearTimeout(timer);
  timer = setTimeout(() => {
    const body = {names: Object.assign({}, names), named: count(names), updatedAt: new Date().toISOString()};
    writing = writing.then(() => db.doc("names/" + uid).set(body)).then(
      () => { $("sync").textContent = "Saved for Claude to read."; },
      () => { $("sync").textContent = "Saved on this device only. Copy the names JSON before you leave."; });
  }, 800);
}
$("list").addEventListener("input", (e) => {
  const row = e.target.closest(".row"); if (!row) return;
  names[row.dataset.k] = e.target.value;
  if (!clean(e.target.value)) delete names[row.dataset.k];
  saveLocal(); queueRemote(); refresh();
});
$("list").addEventListener("keydown", (e) => {
  if (e.key !== "Enter") return;
  const row = e.target.closest(".row"); if (!row) return;
  e.preventDefault();
  const inp = e.target;
  if (!clean(inp.value)) {
    const p = inp.placeholder, taken = SHIPPED.has(p) || Object.values(names).some((v) => clean(v) === p);
    if (!taken) { inp.value = p; names[row.dataset.k] = p; saveLocal(); queueRemote(); refresh(); }
  }
  const all = Array.from(document.querySelectorAll(".row:not([hidden]) input"));
  const next = all[all.indexOf(inp) + 1];
  if (next) next.focus();
});
$("show").addEventListener("change", () => filter());
$("q").addEventListener("input", () => filter());
$("copy").addEventListener("click", () => {
  const t = $("json").value, ta = $("json");
  const fb = () => { ta.focus(); ta.select(); $("copy-status").textContent = "Clipboard blocked: the text is selected, copy it by hand."; };
  try { navigator.clipboard.writeText(t).then(() => { $("copy-status").textContent = "Names copied."; }, fb); } catch (e) { fb(); }
});

names = loadLocal();
build();
refresh();
(async () => {
  try {
    if (!window.claude || !window.claude.use) return;
    const [d, u] = await Promise.all([window.claude.use("db"), window.claude.use("user")]);
    if (!d || !u) return;
    const id = await u.id(); if (!id) return;
    db = d; uid = id;
    const snap = await db.doc("names/" + uid).get();
    if (snap.exists) {
      const remote = JSON.parse(JSON.stringify((snap.data() || {}).names || {}));
      if (count(remote) > count(names)) {
        names = remote; saveLocal();
        document.querySelectorAll(".row").forEach((el) => { el.querySelector("input").value = names[el.dataset.k] || ""; });
        refresh();
      }
    }
    $("sync").textContent = "Signed in: names are saved for Claude to read.";
  } catch (e) { /* stays local */ }
})();
</script>
'''

if __name__ == "__main__":
    main()
