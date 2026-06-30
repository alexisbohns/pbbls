// apps/admin/app/(authed)/playground/glyphs/page.tsx
import { svgToStrokes } from "@/lib/pebblestore/svg-to-strokes"
import { GlyphPreview } from "@/components/pebblestore/GlyphPreview"
import { IDENTITY_ADJUST } from "@/lib/pebblestore/types"

const SAMPLES: { label: string; svg: string }[] = [
  {
    label: "Line art (path L)",
    svg: `<svg viewBox="0 0 100 100"><path d="M10 90 L50 10 L90 90 Z"/></svg>`,
  },
  {
    label: "Curves (path Q/C)",
    svg: `<svg viewBox="0 0 100 100"><path d="M10 50 Q50 10 90 50 C90 80 10 80 10 50"/></svg>`,
  },
  {
    label: "Polyline + line",
    svg: `<svg viewBox="0 0 100 100"><polyline points="10,10 50,90 90,10"/><line x1="10" y1="50" x2="90" y2="50"/></svg>`,
  },
  {
    label: "Unsupported mix (rect skipped, arc skipped)",
    svg: `<svg viewBox="0 0 100 100"><rect x="10" y="10" width="80" height="80"/><path d="M10 10 A 40 40 0 0 1 90 90"/><path d="M10 90 L90 10"/></svg>`,
  },
  {
    label: "Exponent coords (1e1 = 10)",
    svg: `<svg viewBox="0 0 100 100"><path d="M1e1 9e1 L5e1 1e1 L9e1 9e1"/></svg>`,
  },
]

export default function GlyphPlayground() {
  return (
    <div className="space-y-8">
      <h1 className="text-lg font-semibold">Glyph conversion playground</h1>
      <div className="grid grid-cols-2 gap-6 md:grid-cols-4">
        {SAMPLES.map((s) => {
          const r = svgToStrokes(s.svg)
          return (
            <div key={s.label} className="space-y-2 rounded-lg border p-3 text-xs">
              <div className="font-medium">{s.label}</div>
              <GlyphPreview
                strokes={r.strokes}
                glyphViewBox={r.viewBox}
                shape={null}
                adjust={{ ...IDENTITY_ADJUST, scale: 0.8 }}
                className="aspect-square w-full text-foreground"
              />
              <div className="text-muted-foreground">strokes: {r.strokes.length}</div>
              <div className="text-muted-foreground">skipped: {r.skipped.join(", ") || "none"}</div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
