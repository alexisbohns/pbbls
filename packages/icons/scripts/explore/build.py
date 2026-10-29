import sys, os, re, json, html, collections, xml.dom.minidom as md
# Usage: python3 -I build.py <wave>   (writes <wave>/gallery/)
ROOT=sys.argv[1]; OUT=os.path.join(ROOT,'gallery'); os.makedirs(os.path.join(OUT,'hands'),exist_ok=True)
# The hands' sprites name their symbols by the brief's source id (checkmark, home, ...);
# the wave's library.json maps each chosen drawing back to that id.
lib=json.load(open(os.path.join(ROOT,'library.json')))
IDS=list(dict.fromkeys(e['source'] for e in lib if e['kind']=='chosen'))
ACT=[e['source'] for e in lib if e['kind']=='chosen' and e['hasActive']]
ALL=IDS+[a+"-active" for a in ACT]
notes=json.load(open(os.path.join(ROOT,'notes.json'))) if os.path.exists(os.path.join(ROOT,'notes.json')) else {}
ACT=set(ACT)

FONT='<link href="https://fonts.googleapis.com/css2?family=Ysabeau:ital,wght@0,1..1000;1,1..1000&family=Ysabeau+SC:wght@1..1000&display=swap" rel="stylesheet">'
CSS='''
:root{--ground:#ECE8DF;--stock:#FBF9F4;--ink:#1F1D1A;--ink-2:#686255;--rule:rgba(31,29,26,.14);--card-rule:rgba(31,29,26,.22);
--paper:#FEF9EF;--paper-ink:#1D1B16;--paper-ink2:#4C4639;--paper-out:#7D7667;--paper-rule:rgba(29,27,22,.12);--paper-accent:#8E4955;--paper-accent-c:#FFD9DD;
--night:#131315;--night-ink:#E7E2D9;--night-ink2:#CFC6B4;--night-out:#989080;--night-rule:rgba(231,226,217,.12);--night-accent:#FFB2BC;--night-accent-c:#72333E;}
@media (prefers-color-scheme:dark){:root:not([data-theme="light"]){--ground:#141416;--stock:#1E1D1B;--ink:#ECE6DA;--ink-2:#A59E91;--rule:rgba(236,230,218,.12);--card-rule:rgba(236,230,218,.18);color-scheme:dark}}
:root[data-theme="dark"]{--ground:#141416;--stock:#1E1D1B;--ink:#ECE6DA;--ink-2:#A59E91;--rule:rgba(236,230,218,.12);--card-rule:rgba(236,230,218,.18);color-scheme:dark}
*{box-sizing:border-box}body{margin:0;background:var(--ground);color:var(--ink);font-family:"Ysabeau",system-ui,sans-serif;font-size:16.5px;line-height:1.5}
.wrap{max-width:1180px;margin:0 auto;padding-left:max(16px,env(safe-area-inset-left,0px));padding-right:max(16px,env(safe-area-inset-right,0px));padding-block:36px 80px}
.kick{font-family:"Ysabeau SC";letter-spacing:.14em;font-weight:600;font-size:12px;color:var(--ink-2)}
h1{font-weight:300;font-size:clamp(30px,5vw,46px);line-height:1.05;letter-spacing:-.02em;margin:8px 0 12px;text-wrap:balance}
h2{font-family:"Ysabeau SC";font-weight:600;font-size:13px;letter-spacing:.18em;color:var(--ink-2);margin:44px 0 12px;padding-top:12px;border-top:1px solid var(--rule)}
.lede{font-size:18px;font-style:italic;max-width:64ch;margin:0;font-weight:380}p{max-width:68ch}a{color:var(--ink);text-decoration-color:var(--card-rule);text-underline-offset:4px}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:18px}
.card{background:var(--stock);border:1px solid var(--card-rule);border-radius:16px;padding:14px;display:flex;flex-direction:column;min-width:0}
.card h3{margin:10px 0 4px;font-weight:400;font-size:22px;line-height:1.15}.card p{font-size:15px;margin:0;color:var(--ink-2)}
.card .verdict{font-size:15px;color:var(--ink);margin-top:8px;border-top:1px solid var(--rule);padding-top:8px}
.open{display:inline-flex;align-items:center;min-height:44px;margin-top:8px;font-family:"Ysabeau SC";font-weight:600;letter-spacing:.14em;font-size:12.5px}
.thumb{display:grid;grid-template-columns:repeat(2,1fr);gap:8px}
.ph{border-radius:10px;padding:10px;display:flex;gap:10px;align-items:center;justify-content:space-around}
.ph svg{width:26px;height:26px}
.panels{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:16px}
.panel{border-radius:18px;padding:16px 14px;display:flex;flex-direction:column;gap:14px;min-width:0;font-family:-apple-system,BlinkMacSystemFont,"SF Pro Text",system-ui,sans-serif;font-size:16px}
.panel .pk{font-family:"Ysabeau SC";font-size:11px;letter-spacing:.14em;font-weight:600;opacity:.7}
.paper{background:var(--paper);color:var(--paper-ink);--i:var(--paper-ink);--i2:var(--paper-ink2);--o:var(--paper-out);--r:var(--paper-rule);--a:var(--paper-accent);--ac:var(--paper-accent-c);--cell:#FFFFFF;--bar:rgba(255,255,255,.75)}
.night{background:var(--night);color:var(--night-ink);--i:var(--night-ink);--i2:var(--night-ink2);--o:var(--night-out);--r:var(--night-rule);--a:var(--night-accent);--ac:var(--night-accent-c);--cell:#1C1C1E;--bar:rgba(28,28,30,.85)}
.acc .lead svg,.acc .tab.on,.acc .tool svg,.acc .like.on svg{color:var(--a)}
.ink .tab.on{color:var(--i)}
.menu{background:var(--cell);border-radius:12px;overflow:hidden}
.row{display:flex;align-items:center;gap:12px;min-height:44px;padding:0 14px;position:relative}
.row+.row::before{content:"";position:absolute;left:50px;right:0;top:0;height:1px;background:var(--r)}
.lead{width:24px;height:24px;flex:none;display:flex}.lead svg{width:24px;height:24px}
.row .lab{flex:1;min-width:0;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.row .chev svg{width:14px;height:14px;color:var(--o)}
.tabs{display:flex;justify-content:space-around;background:var(--bar);border-top:1px solid var(--r);padding:8px 0 4px;border-radius:12px}
.tab{display:flex;flex-direction:column;align-items:center;gap:2px;font-size:10px;color:var(--o);width:56px}
.tab svg{width:26px;height:26px}.tab.on{font-weight:600}
.tools{display:flex;justify-content:space-between;align-items:center;padding:4px 10px}
.tool svg{width:24px;height:24px}.tool{color:var(--i2)}
.ink .tool svg{color:var(--i)}
.btn{display:flex;align-items:center;justify-content:center;gap:8px;min-height:44px;border-radius:12px;background:var(--cell);border:1px solid var(--r);font-weight:500}
.btn svg{width:22px;height:22px}
.acc .btn.primary{background:var(--a);color:var(--night);border-color:transparent}
.night.acc .btn.primary{color:var(--night)}
.paper.acc .btn.primary{color:#fff}
.check{display:flex;flex-direction:column;background:var(--cell);border-radius:12px;overflow:hidden}
.check .row .lab.done{color:var(--o);text-decoration:line-through}
.acc .check .lead.done svg{color:var(--a)}
.likes{display:flex;gap:18px;align-items:center;padding:0 6px}
.like svg{width:24px;height:24px;color:var(--i2)}.like.on svg{color:var(--i)}
.strip{display:grid;grid-template-columns:repeat(auto-fill,minmax(64px,1fr));gap:8px}
.strip div{display:flex;flex-direction:column;align-items:center;gap:4px;font-size:10px;color:var(--ink-2);padding:8px 2px;background:var(--stock);border:1px solid var(--rule);border-radius:10px}
.strip svg{width:24px;height:24px;color:var(--ink)}
.strip.big svg{width:48px;height:48px}
.notes{background:var(--stock);border:1px solid var(--card-rule);border-radius:16px;padding:16px 20px;font-size:15.5px;white-space:pre-wrap}
.specs{display:flex;flex-direction:column;gap:14px}.spec-row h3{font-family:"Ysabeau SC";font-weight:600;font-size:12px;letter-spacing:.14em;color:var(--ink-2);margin:0 0 6px}.spec-row .strip{grid-template-columns:repeat(auto-fill,minmax(96px,1fr))}.spec-row .strip div{flex-direction:row;flex-wrap:wrap;justify-content:center;gap:6px}.spec-row .strip svg{width:28px;height:28px}
.crit{border-left:3px solid var(--ink-2);padding-left:14px;font-size:16px;max-width:70ch}
'''

def U(i,cls=''): return f'<svg class="{cls}"><use href="#{i}"/></svg>'
CHEV='<span class="chev"><svg viewBox="0 0 8 14" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M1 1l6 6-6 6"/></svg></span>'
MENU=[("pebbles","Pebbles"),("glyphs","Glyphs"),("karma","Karma"),("calendar","Calendar"),("stack","Stack"),("pack","Pack"),("track","Track"),("settings","Settings"),("idea","Idea"),("success","Success")]
TABS=[("home","Home"),("path","Path"),("people","People"),("collections","Collections"),("user","You")]

def panel(theme,mode,active_tab):
    cls=f'panel {theme} {mode}'
    rows=''.join(f'<div class="row"><span class="lead">{U(i)}</span><span class="lab">{l}</span>{CHEV}</div>' for i,l in MENU)
    tabs=''.join(f'<div class="tab{" on" if t==active_tab else ""}">{U(t+"-active" if t==active_tab else t)}<span>{l}</span></div>' for t,l in TABS)
    tools=''.join(f'<span class="tool">{U(i)}</span>' for i in ["camera","hourglass","trash","settings","plus-circle"])
    chk=[("First moment",True),("First pebble",True),("First collection",False),("First soul",False),("First glyph",False)]
    check=''.join(f'<div class="row"><span class="lead{" done" if d else ""}">{U("check-circle" if d else "stepper")}</span><span class="lab{" done" if d else ""}">{l}</span>{"" if d else CHEV}</div>' for l,d in chk)
    return f'''<div class="{cls}"><div class="pk">{theme} · {"accent" if mode=="acc" else "ink"}</div>
<div class="btn primary">{U("plus-circle")}<span>New pebble</span></div>
<div class="tools">{tools}</div>
<div class="check">{check}</div>
<div class="menu">{rows}</div>
<div class="likes"><span class="like">{U("like")}</span><span class="like on">{U("like-active")}</span><span class="like">{U("checkmark")}</span><span class="like">{U("success")}</span></div>
<div class="tabs">{tabs}</div></div>'''

hands=[]
for h in sorted(os.listdir(os.path.join(ROOT,'hands'))):
    sp=os.path.join(ROOT,'hands',h,'sprite.svg')
    if not os.path.exists(sp): continue
    raw=open(sp).read()
    try: md.parseString(raw); ok=True
    except Exception as e: ok=False; print(h,'PARSE ERROR',e)
    ids=re.findall(r'<symbol[^>]*id="([^"]+)"',raw)
    missing=[i for i in ALL if i not in ids]
    if missing: print(h,'missing',missing)
    inner=re.sub(r'^\s*<svg[^>]*>|</svg>\s*$','',raw.strip())
    sprite=f'<svg xmlns="http://www.w3.org/2000/svg" style="display:none">{inner}</svg>'
    n=notes.get(h,{}); name=n.get('name',h)
    nmd=open(os.path.join(ROOT,'hands',h,'NOTES.md')).read() if os.path.exists(os.path.join(ROOT,'hands',h,'NOTES.md')) else ''
    panels=panel('paper','ink','home')+panel('paper','acc','people')+panel('night','ink','path')+panel('night','acc','collections')
    strip=''.join(f'<div>{U(i)}<span>{i}</span></div>' for i in ALL)
    big=''.join(f'<div>{U(i)}<span>{i}</span></div>' for i in ALL)
    crit=n.get('critique','')
    alt_html=''
    if h=='h99':
        A=json.load(open(os.path.join(ROOT,'alternates.json')))
        defs=''
        for ic in IDS:
            if ic not in A: continue
            cells=f'<div><svg><use href="#{ic}"/></svg>{("<svg><use href=\"#"+ic+"-active\"/></svg>") if ic in ACT else ""}<span>chosen</span></div>'
            for a in A[ic]:
                hh=a['hand']; defs+=hh
                cells+=f'<div><svg><use href="#{hh}-{ic}"/></svg>{("<svg><use href=\"#"+hh+"-"+ic+"-active\"/></svg>") if ic in ACT else ""}<span>{html.escape(notes.get(hh,{}).get("name",hh))}</span><span>{a["kind"]} ×{a["contexts"]}</span></div>'
            alt_html+=f'<div class="spec-row"><h3>{ic}</h3><div class="strip">{cells}</div></div>'
        hs=sorted(set(a['hand'] for v in A.values() for a in v))
        altsprites=''.join('<svg xmlns="http://www.w3.org/2000/svg" style="display:none">'+re.sub(r'^\s*<svg[^>]*>|</svg>\s*$','',open(os.path.join(ROOT,'hands',hh,'sprite.svg')).read().strip()).replace('id="','id="'+hh+'-')+'</svg>' for hh in hs)
        alt_html=altsprites+'<h2>Saved alternates per icon</h2><p>The chosen drawing first, then the specials (liked, not for this concept) and runners-up (ranked twice or more without a win) kept for the final pass and later waves.</p><div class="specs">'+alt_html+'</div>'
    page=f'''<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"><title>{html.escape(name)}</title>
<meta name="claude:viewport-fit-sides" content="cover">{FONT}<style>{CSS}</style></head><body>{sprite}
<div class="wrap"><div class="kick"><a href="../index.html">← All hands</a> · {h}</div><h1>{html.escape(name)}</h1>
<p class="lede">The same set in situation: a primary button, a toolbar, the first-five checklist, an inset menu, the like toggle and the tab bar. Paper and Night, in ink and in the accent.</p>
<h2>In situation</h2><div class="panels">{panels}</div>
{alt_html}
<h2>Critic's notes</h2><div class="crit">{crit or "<i>Not yet judged.</i>"}</div>
<h2>The hand's notes</h2><div class="notes">{html.escape(nmd)}</div>
<h2>Full set at 24</h2><div class="strip">{strip}</div>
<h2>Full set at 48</h2><div class="strip big">{big}</div>
</div></body></html>'''
    open(os.path.join(OUT,'hands',f'{h}.html'),'w').write(page)
    hands.append((h,name,sprite,n))

def card(h,name,sprite,n):
    ph=lambda th,mode: f'<div class="ph panel {th} {mode}">'+''.join(f'<span class="tab{" on" if i=="home" else ""}">{U(i+"-active" if i=="home" else i)}</span>' for i in ["home","path","people","collections","plus-circle","like-active"])+'</div>'
    return f'''<div class="card">{sprite.replace('id="','id="'+h+'-')}<div class="thumb">{ph("paper","ink").replace('href="#','href="#'+h+'-')}{ph("night","acc").replace('href="#','href="#'+h+'-')}</div>
<h3>{html.escape(name)}</h3><p>{html.escape(n.get("owns",""))}</p><div class="verdict">{n.get("verdict","")}</div><a class="open" href="hands/{h}.html">Open the hand →</a></div>'''
r0=''.join(card(*x) for x in hands if x[0]=='h00')
r3=''.join(card(*x) for x in hands if 21<=int(x[0][1:])<=23)
rf=''.join(card(*x) for x in hands if x[0]=='h99')
r1=''.join(card(*x) for x in hands if 1<=int(x[0][1:])<=10)
r2a=''.join(card(*x) for x in hands if 11<=int(x[0][1:])<=15)
r2b=''.join(card(*x) for x in hands if int(x[0][1:])>=16)
sp=json.load(open(os.path.join(ROOT,'specials2.json')))
byicon=collections.OrderedDict()
for ic in IDS:
    rows=[(h,n) for i,h,n in sp if i==ic]
    if rows: byicon[ic]=sorted(rows,key=lambda r:(-r[1],r[0]))
def section(entries):
    by=collections.OrderedDict()
    for ic in IDS:
        rows=[(h,n) for i,h,n in entries if i==ic]
        if rows: by[ic]=sorted(rows,key=lambda r:(-r[1],r[0]))
    out=''
    for ic,rows in by.items():
        cells=''.join(f'<div><svg><use href="#{h}-{ic}"/></svg>{("<svg><use href=\"#"+h+"-"+ic+"-active\"/></svg>") if ic in ACT else ""}<span>{html.escape(notes.get(h,{}).get("name",h))}</span><span>{n}×</span></div>' for h,n in rows)
        out+=f'<div class="spec-row"><h3>{ic}</h3><div class="strip">{cells}</div></div>'
    return out
run_html=section(json.load(open(os.path.join(ROOT,'runnersup.json'))))
ALTS=json.load(open(os.path.join(ROOT,'alternates.json')))
_bh=collections.defaultdict(list)
for _ic,_v in ALTS.items():
    for _a in _v: _bh[_a['hand']].append((_ic,_a['kind'],_a['contexts']))
byhand_html=''
for _h in sorted(_bh):
    cells=''.join(f'<div><svg><use href="#{_h}-{ic}"/></svg>{("<svg><use href=\"#"+_h+"-"+ic+"-active\"/></svg>") if ic in ACT else ""}<span>{ic}</span><span>{k[0]}{n}</span></div>' for ic,k,n in sorted(_bh[_h]))
    byhand_html+=f'<div class="spec-row"><h3>{_h} · {html.escape(notes.get(_h,{}).get("name",_h))} · {len(_bh[_h])}</h3><div class="strip">{cells}</div></div>'

spec_html=''
for ic,rows in byicon.items():
    cells=''.join(f'<div><svg><use href="#{h}-{ic}"/></svg>{("<svg><use href=\"#"+h+"-"+ic+"-active\"/></svg>") if ic in ACT else ""}<span>{html.escape(notes.get(h,{}).get("name",h))}</span><span>{n}×</span></div>' for h,n in rows)
    spec_html+=f'<div class="spec-row"><h3>{ic}</h3><div class="strip">{cells}</div></div>'
hub=f'''<title>Pebbles Icon Hands</title>
<meta name="claude:viewport-fit-sides" content="cover">{FONT}<style>{CSS}</style>
<div class="wrap"><div class="kick">Pebbles · icon set · 28 October 2026</div><h1>Twenty-three hands, one set of twenty-nine icons</h1>
<p class="lede">Every hand drew the whole set on a 24-unit square, stroke 1.75, no fills; active means something happened inside the drawing. Round 1 worked from a DNA paragraph that listed example subjects, and all ten hands drew them. Round 2 removed the list: five hands drew free, five grew their world from a random seed. Open a hand to see it in situation.</p>
<p><a class="open" href="library.html">The library under its official names →</a> &nbsp; <a class="open" href="lit.html">The lit series →</a> &nbsp; <a class="open" href="game.html">Play the blind pick-three game →</a></p>
<h2>The final set · foundation wave</h2><div class="grid">{rf}</div>
<h2>Round 3 · verdict</h2><div class="crit">{notes.get("_round3","")}</div>
<h2>Round 3 · the composite redrawn in one line</h2><div class="grid">{r3}</div>
<h2>After the game · the composite</h2><div class="grid">{r0}</div>
<h2>After the game · specials marked in two or three contexts</h2><p>Drawings you liked but not for the concept they competed for, kept only when the mark repeated across contexts. Base and active twin where one exists.</p><div class="specs">{spec_html}</div>
<h2>After the game · runners-up, in the top three twice or more without a win</h2><p>Drawings you ranked for the concept in at least two contexts but never first. The named count is how many contexts placed them.</p><div class="specs">{run_html}</div>
<h2>After the game · the same saved drawings, by hand</h2><p>s = special, r = runner-up, then the number of contexts.</p><div class="specs">{byhand_html}</div>
<h2>Round 2 · verdict</h2><div class="crit">{notes.get("_round2","<i>Not yet judged.</i>")}</div>
<h2>Round 2 · five free hands</h2><div class="grid">{r2a}</div>
<h2>Round 2 · five seeded hands</h2><div class="grid">{r2b}</div>
<h2>Round 1 · verdict</h2><div class="crit">{notes.get("_round","")}</div>
<h2>Round 1 · ten hands from the listed DNA</h2><div class="grid">{r1}</div></div>'''
open(os.path.join(OUT,'index.html'),'w').write(hub)
# Library page: official names grouped by prefix
libsvg='<svg xmlns="http://www.w3.org/2000/svg" style="display:none">'+re.sub(r'^\s*<svg[^>]*>|</svg>\s*$','',open(os.path.join(ROOT,'library.svg')).read().strip())+'</svg>'
groups=collections.OrderedDict()
for e in lib: groups.setdefault(e['name'].split('-')[0],[]).append(e)
multi=[(p,v) for p,v in groups.items() if len(v)>1]; singles=[e for p,v in groups.items() if len(v)==1 for e in v]
def cell(e):
    return f'<div><svg><use href="#{e["name"]}"/></svg>{("<svg><use href=\"#"+e["name"]+"-active\"/></svg>") if e["hasActive"] else ""}<span>{e["name"]}</span><span>{e["hand"]} · {e["kind"]}</span></div>'
body=''.join(f'<div class="spec-row"><h3>{p} · {len(v)}</h3><div class="strip">{"".join(cell(e) for e in v)}</div></div>' for p,v in multi)
body+=f'<div class="spec-row"><h3>singles · {len(singles)}</h3><div class="strip">{"".join(cell(e) for e in singles)}</div></div>'
chosen=''.join(cell(e) for e in lib if e['kind']=='chosen')
page=f'''<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"><title>Pebbles Icon Library</title>
<meta name="claude:viewport-fit-sides" content="cover">{FONT}<style>{CSS}</style></head><body>{libsvg}
<div class="wrap"><div class="kick"><a href="index.html">← All hands</a> · library</div><h1>The library, under its official names</h1>
<p class="lede">{len(lib)} drawings kept from the foundation wave: the 23 chosen icons and every saved alternate, renamed by the maintainer on 29 October 2026 and grouped by the first word of the name.</p>
<h2>The chosen 23</h2><div class="strip">{chosen}</div>
<h2>By prefix</h2><div class="specs">{body}</div>
</div></body></html>'''
open(os.path.join(OUT,'library.html'),'w').write(page)
fam=json.load(open(os.path.join(ROOT,'lit-families.json')))
srcsvg='<svg xmlns="http://www.w3.org/2000/svg" style="display:none">'+re.sub(r'^\s*<svg[^>]*>|</svg>\s*$','',open(os.path.join(ROOT,'lit-sources.svg')).read().strip())+'</svg>'
litdefs=''.join('<svg xmlns="http://www.w3.org/2000/svg" style="display:none">'+re.sub(r'^\s*<svg[^>]*>|</svg>\s*$','',open(os.path.join(ROOT,'lit',h,'sprite.svg')).read().strip()).replace('id="','id="'+h+'-')+'</svg>' for h in ('L1','L4'))
litbody=''
for f,ns in fam.items():
    rows=''
    for n in ns:
        cells=f'<div><svg><use href="#{n}"/></svg><span>{n}</span></div>'
        for k in (1,2,3): cells+=f'<div><svg><use href="#L4-{n}-lit-{k}"/></svg><span>lit-{k}</span></div>'
        for k in (1,2,3): cells+=f'<div class="dim"><svg><use href="#L1-{n}-lit-{k}"/></svg><span>dots {k}</span></div>'
        rows+=f'<div class="spec-row"><div class="strip litrow">{cells}</div></div>'
    litbody+=f'<h2>{f}</h2>{rows}'
litpage=f'''<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"><title>Pebbles Lit Series</title>
<meta name="claude:viewport-fit-sides" content="cover">{FONT}<style>{CSS}.litrow{{grid-template-columns:repeat(7,minmax(64px,1fr))}}.dim{{opacity:.45}}</style></head><body>{srcsvg}{litdefs}
<div class="wrap"><div class="kick"><a href="index.html">← All hands</a> · lit series</div><h1>The lit series: one, two, three stones lit</h1>
<p class="lede">Forty drawings from the library that hold stones, each with up to three lit variants in walking, stacking or growing order. The solid stones (L4) are the kept version, as the foundation lights its active twins. The dimmed dot version (L1–L3, three hands that converged) was rejected: a ring with a dot reads as an eye. Five drawings have nothing to fill: fall, calendar-date, calendar-dot, abstract-stack, abstract-cairn.</p>
{litbody}</div></body></html>'''
open(os.path.join(OUT,'lit.html'),'w').write(litpage)
print('built',len(hands),'hands')
