"use client"

import { useEffect, useState } from "react"
import { contrastRatio, toHex, type Rgb } from "@/lib/theme/contrast"
import { cn } from "@/lib/utils"

/**
 * Resolves a custom property to sRGB. getComputedStyle hands back whatever
 * syntax the token was written in (hex, oklch…), so paint it on one canvas
 * pixel and read that back.
 */
function resolveVar(name: string): Rgb | null {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  const ctx = value ? document.createElement("canvas").getContext("2d") : null
  if (!ctx) return null
  ctx.fillStyle = value
  ctx.fillRect(0, 0, 1, 1)
  const [r, g, b] = ctx.getImageData(0, 0, 1, 1).data
  return [r, g, b]
}

type ColorSwatchProps = {
  label: string
  /** Custom property for the fill, e.g. "--m3-primary". */
  background: string
  /** Custom property for the text drawn on it; enables the contrast check. */
  foreground?: string
  /** Changes whenever the theme does, so the swatch re-reads its values. */
  themeKey: string
}

export function ColorSwatch({ label, background, foreground, themeKey }: ColorSwatchProps) {
  const [resolved, setResolved] = useState<{ bg: Rgb | null; fg: Rgb | null } | null>(null)

  useEffect(() => {
    // One frame late on purpose: the page applies its <html> classes in its own
    // effect, which runs after this child's effect.
    const frame = requestAnimationFrame(() =>
      setResolved({ bg: resolveVar(background), fg: foreground ? resolveVar(foreground) : null }),
    )
    return () => cancelAnimationFrame(frame)
  }, [background, foreground, themeKey])

  const ratio = resolved?.bg && resolved.fg ? contrastRatio(resolved.bg, resolved.fg) : null

  return (
    <figure className="flex w-44 flex-col overflow-hidden rounded-lg border border-border text-xs">
      <div
        className="flex h-16 items-end p-2"
        style={{ background: `var(${background})`, color: foreground ? `var(${foreground})` : undefined }}
      >
        {foreground && <span className="font-medium">Aa {foreground.replace(/^--(m3-)?/, "")}</span>}
      </div>
      <figcaption className="flex flex-col gap-0.5 bg-card p-2 text-card-foreground">
        <span className="font-mono">{label}</span>
        <span className="font-mono text-muted-foreground">{resolved?.bg ? toHex(resolved.bg) : "—"}</span>
        {ratio !== null && (
          <span className={cn("font-mono", ratio < 4.5 ? "text-destructive" : "text-muted-foreground")}>
            {ratio.toFixed(2)}:1{ratio < 4.5 ? " · below AA" : ""}
          </span>
        )}
      </figcaption>
    </figure>
  )
}
