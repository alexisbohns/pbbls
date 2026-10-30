#!/usr/bin/env python3
"""Build gallery/rename.html: a rename sheet for the chosen icons and their alternates."""
import html, json, re, sys
from pathlib import Path

# Usage: python3 -I rename-build.py <wave>   (writes <wave>/gallery/rename.html)
ROOT = Path(sys.argv[1]).resolve()
# The sprites name their symbols by the brief's source id; library.json maps the chosen drawings back to it.
_lib = json.loads((ROOT / "library.json").read_text(encoding="utf-8"))
ICONS = list(dict.fromkeys(e["source"] for e in _lib if e["kind"] == "chosen"))
ACTIVE = {e["source"] for e in _lib if e["kind"] == "chosen" and e["hasActive"]}

alternates = json.loads((ROOT / "alternates.json").read_text())
notes = json.loads((ROOT / "notes.json").read_text())

def hand_name(h):
    v = notes.get(h)
    if isinstance(v, dict):
        return v.get("name", h)
    return v if isinstance(v, str) else h

SYM_RE = re.compile(r'<symbol\b[^>]*\bid="([^"]+)"[^>]*>.*?</symbol>', re.S)
sprites = {}
def symbols(hand):
    if hand not in sprites:
        text = (ROOT / "hands" / hand / "sprite.svg").read_text()
        sprites[hand] = {m.group(1): m.group(0) for m in SYM_RE.finditer(text)}
    return sprites[hand]

rows = []
for icon in ICONS:
    rows.append({"hand": "h99", "icon": icon, "kind": "chosen", "contexts": None})
    for alt in alternates.get(icon, []):
        rows.append({"hand": alt["hand"], "icon": icon, "kind": alt["kind"], "contexts": alt.get("contexts")})

used = {}
def use(hand, sid):
    syms = symbols(hand)
    if sid not in syms:
        raise SystemExit(f"missing symbol {sid} in {hand}")
    key = f"{hand}-{sid}"
    if key not in used:
        used[key] = re.sub(r'\bid="[^"]+"', f'id="{key}"', syms[sid], count=1)
    return key

def glyph(ref):
    return f'<svg width="32" height="32" aria-hidden="true"><use href="#{ref}"/></svg>'

row_html = []
seen_ids = set()
for r in rows:
    h, i = r["hand"], r["icon"]
    refs = [use(h, i)] + ([use(h, i + "-active")] if i in ACTIVE else [])
    drawn = "".join(glyph(x) for x in refs)
    if r["kind"] == "chosen":
        cap = f"{h} · chosen"
    else:
        cap = f"{h} · {hand_name(h)} · {r['kind']} ×{r['contexts']}"
    key = f"{h}:{i}"
    rid = f"name-{h}-{i}"
    if rid in seen_ids:  # same drawing listed twice (special + runner-up)
        rid = f"{rid}-{r['kind']}"
    seen_ids.add(rid)
    row_html.append(
        f'<li class="row{" chosen" if r["kind"] == "chosen" else ""}" data-hand="{h}" data-icon="{i}" '
        f'data-kind="{r["kind"]}" data-contexts="{"" if r["contexts"] is None else r["contexts"]}">'
        f'<div class="sw paper">{drawn}</div><div class="sw night">{drawn}</div>'
        f'<div class="meta"><div class="key">{html.escape(key)}</div>'
        f'<div class="cap">{html.escape(cap)}</div>'
        f'<label class="vh" for="{rid}">Name for {html.escape(key)}</label>'
        f'<input type="text" id="{rid}" value="{html.escape(i)}" autocomplete="off" autocapitalize="off" spellcheck="false"></div></li>'
    )

sprite = '<svg xmlns="http://www.w3.org/2000/svg" style="position:absolute;width:0;height:0;overflow:hidden" aria-hidden="true">' + "".join(used.values()) + "</svg>"

CSS = """
:root{--bg:#F6F4EF;--surface:#FFFFFF;--text:#1D1B16;--muted:#6B665C;--line:#DDD8CC;--accent:#2F5D8A;--accent-text:#FFFFFF;--chosen:#EEF3F8;color-scheme:light}
@media (prefers-color-scheme: dark){:root:not([data-theme="light"]){--bg:#121214;--surface:#1C1C1F;--text:#E7E2D9;--muted:#9A958B;--line:#34343A;--accent:#8DB4DA;--accent-text:#0E1520;--chosen:#1B232C;color-scheme:dark}}
:root[data-theme="dark"]{--bg:#121214;--surface:#1C1C1F;--text:#E7E2D9;--muted:#9A958B;--line:#34343A;--accent:#8DB4DA;--accent-text:#0E1520;--chosen:#1B232C;color-scheme:dark}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--text);font:15px/1.4 system-ui,-apple-system,"Segoe UI",sans-serif}
.wrap{max-width:860px;margin:0 auto;padding-left:max(16px,env(safe-area-inset-left,0px));padding-right:max(16px,env(safe-area-inset-right,0px));padding-bottom:max(24px,env(safe-area-inset-bottom,0px))}
header{position:sticky;top:0;z-index:2;background:var(--bg);border-bottom:1px solid var(--line);padding:12px 0 10px;display:flex;flex-wrap:wrap;gap:8px;align-items:center}
h1{font-size:17px;margin:0;flex:1 1 100%}
#filter{flex:1 1 180px;min-width:0}
input[type=text],input[type=search]{height:44px;padding:0 12px;border:1px solid var(--line);border-radius:8px;background:var(--surface);color:var(--text);font:inherit;width:100%}
input:focus-visible,button:focus-visible,a:focus-visible{outline:2px solid var(--accent);outline-offset:1px}
button{height:44px;padding:0 14px;border-radius:8px;border:1px solid var(--accent);background:var(--accent);color:var(--accent-text);font:inherit;font-weight:600;cursor:pointer}
button.ghost{background:transparent;color:var(--text);border-color:var(--line)}
.count{font-size:13px;color:var(--muted);flex:1 1 auto}
.tools{display:flex;gap:8px;align-items:center;flex-wrap:wrap}
.reset{color:var(--muted);font-size:13px;text-decoration:underline;cursor:pointer;background:none;border:0;padding:0 4px;height:44px;font-weight:400}
#confirm{display:none;flex:1 1 100%;gap:8px;align-items:center;flex-wrap:wrap;font-size:13px}
#confirm.on{display:flex}
#status{font-size:13px;color:var(--muted);min-height:1em;flex:1 1 100%}
ul{list-style:none;margin:12px 0;padding:0;display:grid;gap:8px}
.row{display:grid;grid-template-columns:auto auto 1fr;gap:10px;align-items:center;background:var(--surface);border:1px solid var(--line);border-radius:10px;padding:8px}
.row.chosen{background:var(--chosen);margin-top:10px}
.row[hidden]{display:none}
.sw{display:flex;gap:6px;padding:8px;border-radius:8px}
.sw.paper{background:#FEF9EF;color:#1D1B16}
.sw.night{background:#131315;color:#E7E2D9}
.sw svg{display:block}
.meta{min-width:0;display:grid;gap:3px}
.key{font:600 14px ui-monospace,SFMono-Regular,Menlo,monospace}
.cap{font-size:12px;color:var(--muted);overflow-wrap:anywhere}
.renamed input{border-color:var(--accent)}
.vh{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0 0 0 0);white-space:nowrap}
#export{width:100%;min-height:160px;font:12px ui-monospace,Menlo,monospace;background:var(--surface);color:var(--text);border:1px solid var(--line);border-radius:8px;padding:8px}
#fallback{display:none;width:100%;min-height:120px;font:12px ui-monospace,Menlo,monospace;flex:1 1 100%;background:var(--surface);color:var(--text);border:1px solid var(--line);border-radius:8px}
#fallback.on{display:block}
@media (max-width:520px){.row{grid-template-columns:auto auto;}.meta{grid-column:1/-1}}
"""

JS = r"""
(function(){
  var PREFIX = 'icon-rename:';
  var inputs = Array.prototype.slice.call(document.querySelectorAll('.row input'));
  var rows = Array.prototype.slice.call(document.querySelectorAll('.row'));
  var total = rows.length;
  function load(k){ try { return localStorage.getItem(PREFIX + k); } catch (e) { return null; } }
  function save(k, v){ try { localStorage.setItem(PREFIX + k, v); } catch (e) {} }
  function drop(k){ try { localStorage.removeItem(PREFIX + k); } catch (e) {} }
  function data(){
    return rows.map(function(r){
      var c = r.getAttribute('data-contexts');
      return { hand: r.getAttribute('data-hand'), icon: r.getAttribute('data-icon'),
               kind: r.getAttribute('data-kind'), contexts: c === '' ? null : Number(c),
               name: r.querySelector('input').value };
    });
  }
  function json(){ return JSON.stringify(data(), null, 1); }
  var countEl = document.getElementById('count');
  var exportEl = document.getElementById('export');
  var statusEl = document.getElementById('status');
  function refresh(){
    var n = 0;
    rows.forEach(function(r){
      var ren = r.querySelector('input').value !== r.getAttribute('data-icon');
      r.classList.toggle('renamed', ren); if (ren) n++;
    });
    countEl.textContent = n + ' of ' + total + ' renamed';
    exportEl.value = json();
  }
  inputs.forEach(function(inp, idx){
    var stored = load(inp.id);
    if (stored !== null) inp.value = stored;
    inp.addEventListener('input', function(){ save(inp.id, inp.value); refresh(); applyFilter(); });
    inp.addEventListener('keydown', function(e){
      if (e.key !== 'Enter') return;
      e.preventDefault();
      for (var j = idx + 1; j < inputs.length; j++) {
        if (!inputs[j].closest('.row').hidden) { inputs[j].focus(); inputs[j].select(); return; }
      }
    });
  });
  var filterEl = document.getElementById('filter');
  function applyFilter(){
    var q = filterEl.value.trim().toLowerCase();
    rows.forEach(function(r){
      if (!q) { r.hidden = false; return; }
      var hay = (r.querySelector('.key').textContent + ' ' + r.querySelector('.cap').textContent + ' ' + r.querySelector('input').value).toLowerCase();
      r.hidden = hay.indexOf(q) === -1;
    });
  }
  filterEl.addEventListener('input', applyFilter);
  var fallback = document.getElementById('fallback');
  function showFallback(text){
    fallback.value = text; fallback.classList.add('on'); fallback.focus(); fallback.select();
    statusEl.textContent = 'Clipboard unavailable: the JSON is selected below, copy it by hand.';
  }
  document.getElementById('copy').addEventListener('click', function(){
    var text = json();
    fallback.classList.remove('on');
    try {
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(function(){
          statusEl.textContent = 'Copied ' + total + ' rows as JSON.';
        }, function(){ showFallback(text); });
      } else { showFallback(text); }
    } catch (e) { showFallback(text); }
  });
  var confirmEl = document.getElementById('confirm');
  document.getElementById('reset').addEventListener('click', function(){ confirmEl.classList.add('on'); document.getElementById('reset-no').focus(); });
  document.getElementById('reset-no').addEventListener('click', function(){ confirmEl.classList.remove('on'); });
  document.getElementById('reset-yes').addEventListener('click', function(){
    rows.forEach(function(r){ var inp = r.querySelector('input'); inp.value = r.getAttribute('data-icon'); drop(inp.id); });
    confirmEl.classList.remove('on'); statusEl.textContent = 'Names reset.'; refresh(); applyFilter();
  });
  refresh();
})();
"""

page = f"""<title>Icon Rename Sheet</title>
<meta name="claude:viewport-fit-sides" content="cover">
<style>{CSS}</style>
{sprite}
<div class="wrap">
<header>
<h1>Icon rename sheet</h1>
<input type="search" id="filter" placeholder="Filter by key, caption or name" aria-label="Filter rows">
<div class="tools"><button type="button" id="copy">Copy JSON</button><button type="button" class="reset" id="reset">Reset names</button></div>
<div class="count" id="count" aria-live="polite">0 of {len(rows)} renamed</div>
<div id="confirm" role="alertdialog" aria-label="Confirm reset"><span>Reset all {len(rows)} names to the icon ids?</span><button type="button" id="reset-yes">Reset</button><button type="button" class="ghost" id="reset-no">Cancel</button></div>
<div id="status" aria-live="polite"></div>
<textarea id="fallback" readonly aria-label="JSON to copy"></textarea>
</header>
<ul id="rows">
{chr(10).join(row_html)}
</ul>
<label for="export" style="font-size:13px;color:var(--muted)">Export JSON</label>
<textarea readonly id="export"></textarea>
</div>
<script>{JS}</script>
"""

out = ROOT / "gallery" / "rename.html"
out.parent.mkdir(exist_ok=True)
out.write_text(page)
print(f"wrote {out} ({out.stat().st_size} bytes, {len(rows)} rows, {len(used)} symbols)")
