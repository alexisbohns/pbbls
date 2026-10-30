#!/usr/bin/env python3
"""Build gallery/pick3.html: a blind per-icon picker between three hands."""
import json, re, sys
from pathlib import Path

# Usage: python3 -I pick3-build.py <wave>   (writes <wave>/gallery/pick3.html)
ROOT = Path(sys.argv[1]).resolve()
HANDS = ["h21", "h22", "h23"]
# The sprites name their symbols by the brief's source id; library.json maps the chosen drawings back to it.
_lib = json.loads((ROOT / "library.json").read_text(encoding="utf-8"))
ICONS = list(dict.fromkeys(e["source"] for e in _lib if e["kind"] == "chosen"))
TWINS = [e["source"] for e in _lib if e["kind"] == "chosen" and e["hasActive"]]

def sprite_block(hand):
    src = (ROOT / "hands" / hand / "sprite.svg").read_text()
    m = re.search(r"<svg[^>]*>(.*)</svg>", src, re.S)
    inner = re.sub(r'id="([^"]+)"', lambda x: f'id="{hand}-{x.group(1)}"', m.group(1))
    return (f'<svg class="sprite" data-hand="{hand}" xmlns="http://www.w3.org/2000/svg" '
            f'aria-hidden="true" width="0" height="0" style="position:absolute">{inner}</svg>')

TEMPLATE = r'''<title>Round Three Picker</title>
<meta name="claude:viewport-fit-sides" content="cover">
<style>
:root{--bg:#f6f4ef;--fg:#1d1b16;--muted:#6b665c;--line:#ddd7cc;--card:#ffffff;--accent:#3a5bd9;--accent-fg:#fff;color-scheme:light}
@media (prefers-color-scheme: dark){:root:not([data-theme="light"]){--bg:#17171a;--fg:#ece7de;--muted:#9c968c;--line:#34333a;--card:#202024;--accent:#8aa2ff;--accent-fg:#111;color-scheme:dark}}
:root[data-theme="dark"]{--bg:#17171a;--fg:#ece7de;--muted:#9c968c;--line:#34333a;--card:#202024;--accent:#8aa2ff;--accent-fg:#111;color-scheme:dark}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--fg);font:15px/1.4 system-ui,-apple-system,"Segoe UI",sans-serif}
.wrap{max-width:1100px;margin:0 auto;padding-left:max(16px,env(safe-area-inset-left,0px));padding-right:max(16px,env(safe-area-inset-right,0px));padding-bottom:48px}
header{position:sticky;top:0;z-index:5;background:var(--bg);border-bottom:1px solid var(--line);padding:12px 0;display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap}
header h1{font-size:17px;margin:0}
.count{font-variant-numeric:tabular-nums;color:var(--muted)}
.row{padding:18px 0;border-bottom:1px solid var(--line)}
.row h2{font-size:14px;font-weight:600;margin:0 0 10px;display:flex;gap:8px;align-items:baseline}
.row h2 .st{font-weight:400;color:var(--muted);font-size:12px}
.cards{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}
@media (max-width:720px){.cards{grid-template-columns:1fr}}
.card{appearance:none;font:inherit;color:inherit;text-align:left;border:2px solid var(--line);border-radius:12px;background:var(--card);padding:0;cursor:pointer;overflow:hidden;display:flex;flex-direction:column}
.card:focus-visible{outline:3px solid var(--accent);outline-offset:2px}
.card[aria-pressed="true"]{border-color:var(--accent);box-shadow:0 0 0 2px var(--accent)}
.card .lab{display:flex;justify-content:space-between;align-items:center;padding:6px 10px;font-weight:600;font-size:13px}
.card .tick{font-size:12px;font-weight:600;color:var(--accent-fg);background:var(--accent);border-radius:999px;padding:1px 8px;visibility:hidden}
.card[aria-pressed="true"] .tick{visibility:visible}
.halves{display:grid;grid-template-columns:1fr 1fr}
.half{display:flex;align-items:center;justify-content:center;gap:10px;padding:14px 6px;flex-wrap:wrap}
.paper{background:#FEF9EF;color:#1D1B16}
.night{background:#131315;color:#E7E2D9}
.grp{display:flex;align-items:center;gap:6px}
.half svg{display:block;flex:none}
footer{margin-top:28px}
footer h2{font-size:17px}
table{border-collapse:collapse;margin:8px 0 16px}
th,td{border-bottom:1px solid var(--line);padding:4px 14px 4px 0;text-align:left;font-variant-numeric:tabular-nums}
ul.wins{columns:3 160px;padding-left:18px;margin:8px 0 16px}
textarea{width:100%;min-height:140px;font:12px/1.4 ui-monospace,Menlo,monospace;background:var(--card);color:var(--fg);border:1px solid var(--line);border-radius:8px;padding:8px}
.btns{display:flex;gap:12px;align-items:center;margin-top:8px;flex-wrap:wrap}
button.b{font:inherit;border:1px solid var(--line);background:var(--card);color:var(--fg);border-radius:8px;padding:6px 14px;cursor:pointer}
button.b.primary{background:var(--accent);color:var(--accent-fg);border-color:var(--accent)}
a.reset{color:var(--muted);cursor:pointer;text-decoration:underline}
.confirm{display:none;gap:8px;align-items:center;flex-wrap:wrap}
.confirm.on{display:flex}
.msg{color:var(--muted);font-size:13px}
</style>
__SPRITES__
<div class="wrap">
<header><h1>Round three: pick per icon</h1><span class="count" id="count">0 of __COUNT__ picked</span></header>
<main id="rows"></main>
<footer>
<h2>Results</h2>
<table><thead><tr><th>Hand</th><th>Rows won</th></tr></thead><tbody id="counts"></tbody></table>
<ul class="wins" id="wins"></ul>
<textarea readonly id="json" aria-label="Results JSON"></textarea>
<div class="btns">
<button class="b primary" type="button" id="copy">Copy</button><span class="msg" id="copymsg" aria-live="polite"></span>
<a class="reset" id="reset" role="button" tabindex="0">Reset</a>
<span class="confirm" id="confirm"><span class="msg">Clear all picks and reshuffle?</span><button class="b" type="button" id="yes">Yes, reset</button><button class="b" type="button" id="no">Cancel</button></span>
</div>
</footer>
</div>
<script>
(function(){
var HANDS=__HANDS__, ICONS=__ICONS__, TWINS=__TWINS__, KEY="pick3-v1", LETTERS=["A","B","C"];
var NS="http://www.w3.org/2000/svg";
function load(){try{var s=JSON.parse(localStorage.getItem(KEY)||"null");return s&&typeof s==="object"?s:null}catch(e){return null}}
function save(){try{localStorage.setItem(KEY,JSON.stringify(state))}catch(e){}}
function shuffle(a){a=a.slice();for(var i=a.length-1;i>0;i--){var j=Math.floor(Math.random()*(i+1));var t=a[i];a[i]=a[j];a[j]=t}return a}
function valid(o){return o&&Array.isArray(o)&&o.length===3&&HANDS.every(function(h){return o.indexOf(h)>=0})}
function fresh(){var o={};ICONS.forEach(function(i){o[i]=shuffle(HANDS)});return{order:o,picks:{}}}
var state=load()||fresh();
if(!state.order)state.order={};if(!state.picks)state.picks={};
ICONS.forEach(function(i){if(!valid(state.order[i]))state.order[i]=shuffle(HANDS);if(state.picks[i]&&HANDS.indexOf(state.picks[i])<0)delete state.picks[i]});
save();
function icon(hand,id,size){var s=document.createElementNS(NS,"svg");s.setAttribute("width",size);s.setAttribute("height",size);s.setAttribute("viewBox","0 0 24 24");s.setAttribute("aria-hidden","true");var u=document.createElementNS(NS,"use");u.setAttribute("href","#"+hand+"-"+id);s.appendChild(u);return s}
function half(cls,hand,name){var d=document.createElement("div");d.className="half "+cls;var ids=TWINS.indexOf(name)>=0?[name,name+"-active"]:[name];[24,48].forEach(function(sz){var g=document.createElement("div");g.className="grp";ids.forEach(function(id){g.appendChild(icon(hand,id,sz))});d.appendChild(g)});return d}
var rowsEl=document.getElementById("rows");
function render(){
  rowsEl.textContent="";
  ICONS.forEach(function(name){
    var row=document.createElement("section");row.className="row";
    var h=document.createElement("h2");h.textContent=name;
    if(TWINS.indexOf(name)>=0){var st=document.createElement("span");st.className="st";st.textContent="base + active";h.appendChild(st)}
    row.appendChild(h);
    var cards=document.createElement("div");cards.className="cards";
    state.order[name].forEach(function(hand,pos){
      var b=document.createElement("button");b.type="button";b.className="card";
      b.setAttribute("aria-pressed",state.picks[name]===hand?"true":"false");
      b.setAttribute("aria-label",name+" option "+LETTERS[pos]);
      var lab=document.createElement("div");lab.className="lab";var l=document.createElement("span");l.textContent=LETTERS[pos];var t=document.createElement("span");t.className="tick";t.textContent="Picked";lab.appendChild(l);lab.appendChild(t);
      var hv=document.createElement("div");hv.className="halves";hv.appendChild(half("paper",hand,name));hv.appendChild(half("night",hand,name));
      b.appendChild(lab);b.appendChild(hv);
      b.addEventListener("click",function(){if(state.picks[name]===hand)delete state.picks[name];else state.picks[name]=hand;save();update()});
      cards.appendChild(b);
    });
    row.appendChild(cards);rowsEl.appendChild(row);
  });
  update();
}
function update(){
  ICONS.forEach(function(name,ri){var bs=rowsEl.children[ri].querySelectorAll(".card");state.order[name].forEach(function(hand,pos){bs[pos].setAttribute("aria-pressed",state.picks[name]===hand?"true":"false")})});
  var n=ICONS.filter(function(i){return state.picks[i]}).length;
  document.getElementById("count").textContent=n+" of "+ICONS.length+" picked";
  var counts={};HANDS.forEach(function(h){counts[h]=0});
  var picks={};ICONS.forEach(function(i){if(state.picks[i]){picks[i]=state.picks[i];counts[state.picks[i]]++}});
  var tb=document.getElementById("counts");tb.textContent="";
  HANDS.forEach(function(h){var tr=document.createElement("tr");var a=document.createElement("td");a.textContent=h;var b=document.createElement("td");b.textContent=counts[h];tr.appendChild(a);tr.appendChild(b);tb.appendChild(tr)});
  var ul=document.getElementById("wins");ul.textContent="";
  ICONS.forEach(function(i){var li=document.createElement("li");li.textContent=i+": "+(picks[i]||"—");ul.appendChild(li)});
  document.getElementById("json").value=JSON.stringify({picks:picks,counts:counts},null,2);
}
var ta=document.getElementById("json"),msg=document.getElementById("copymsg");
document.getElementById("copy").addEventListener("click",function(){
  function fallback(){ta.focus();ta.select();msg.textContent="Selected, press copy"}
  try{if(navigator.clipboard&&navigator.clipboard.writeText){navigator.clipboard.writeText(ta.value).then(function(){msg.textContent="Copied"},fallback)}else fallback()}catch(e){fallback()}
});
var conf=document.getElementById("confirm"),rs=document.getElementById("reset");
function ask(){conf.classList.add("on");rs.style.display="none"}
function close(){conf.classList.remove("on");rs.style.display=""}
rs.addEventListener("click",ask);rs.addEventListener("keydown",function(e){if(e.key==="Enter"||e.key===" "){e.preventDefault();ask()}});
document.getElementById("no").addEventListener("click",close);
document.getElementById("yes").addEventListener("click",function(){state=fresh();save();close();msg.textContent="";render()});
render();
})();
</script>
'''

def main():
    sprites = "\n".join(sprite_block(h) for h in HANDS)
    out = (TEMPLATE.replace("__SPRITES__", sprites)
           .replace("__HANDS__", json.dumps(HANDS))
           .replace("__ICONS__", json.dumps(ICONS))
           .replace("__TWINS__", json.dumps(TWINS))
           .replace("__COUNT__", str(len(ICONS))))
    dest = ROOT / "gallery" / "pick3.html"
    dest.parent.mkdir(exist_ok=True)
    dest.write_text(out)
    print(f"wrote {dest} ({dest.stat().st_size} bytes, {len(ICONS)} rows)")

if __name__ == "__main__":
    main()
