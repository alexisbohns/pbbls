import re, math, sys
W = 1.75  # knockout width = stroke width
def cubic(p0,p1,p2,p3,t):
    u=1-t
    return (u*u*u*p0[0]+3*u*u*t*p1[0]+3*u*t*t*p2[0]+t*t*t*p3[0],
            u*u*u*p0[1]+3*u*u*t*p1[1]+3*u*t*t*p2[1]+t*t*t*p3[1])
def sample(d, n=20):
    toks=re.findall(r'[MC]|-?\d*\.?\d+', d.replace(',',' '))
    pts=[]; i=0; cur=None
    while i<len(toks):
        if toks[i]=='M': cur=(float(toks[i+1]),float(toks[i+2])); pts.append(cur); i+=3
        elif toks[i]=='C':
            p1=(float(toks[i+1]),float(toks[i+2])); p2=(float(toks[i+3]),float(toks[i+4])); p3=(float(toks[i+5]),float(toks[i+6]))
            for k in range(1,n+1): pts.append(cubic(cur,p1,p2,p3,k/n))
            cur=p3; i+=7
        else: i+=1
    return pts
from shapely.geometry import LineString
def outline(pts, hw):
    poly=LineString(pts).buffer(hw, cap_style="round", join_style="round", quad_segs=6)
    ring=list(poly.exterior.coords)
    return 'M'+' L'.join(f'{x:.2f} {y:.2f}' for x,y in ring[:-1])+' Z'
lib=open('library.svg').read()
def sym(name, src=lib):
    return re.search(r'<symbol id="%s"[^>]*>(.*?)</symbol>'%re.escape(name), src, re.S).group(1)
def glyph_d(name):
    paths=re.findall(r'<path d="([^"]+)"', sym(name))
    return paths[1]  # second path = the mark
new={}
for base in ['pebble-dotted-glyph','pebble-dotted-glyph-2','pebble-outlined-glyph','pebble-outlined-glyph-2','pebble-round','pebble-flat']:
    lit=base+'-lit-1'
    stone=re.search(r'd="([^"]+)"', sym(lit)).group(1).split(' Z')[0]+' Z'
    carve=outline(sample(glyph_d(base)), W/2)
    new[lit]=f'<path fill="currentColor" fill-rule="evenodd" stroke="none" d="{stone} {carve}"/>'
for f in ['library.svg','lit/L4/sprite.svg','lit/L5/sprite.svg']:
    s=open(f).read()
    for lit,body in new.items():
        s,n=re.subn(r'(<symbol id="%s"[^>]*>).*?(</symbol>)'%re.escape(lit), lambda m: m.group(1)+body+m.group(2), s, flags=re.S)
        print(f, lit, n)
    open(f,'w').write(s)
# preview sheet
cells=''.join(f'<svg viewBox="0 0 24 24" width="96" height="96" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">{sym(b)}</svg><svg viewBox="0 0 24 24" width="96" height="96">{new[b+"-lit-1"]}</svg>' for b in ['pebble-dotted-glyph','pebble-dotted-glyph-2','pebble-outlined-glyph','pebble-outlined-glyph-2'])
open('gallery/carve-preview.html','w').write(f'<!doctype html><body style="background:#FEF9EF;color:#1D1B16;padding:20px"><div style="display:grid;grid-template-columns:repeat(4,96px);gap:12px">{cells}</div><div style="background:#131315;color:#E7E2D9;padding:12px;display:grid;grid-template-columns:repeat(4,96px);gap:12px">{cells}</div></body>')
