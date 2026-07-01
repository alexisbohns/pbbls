"use client"

import { useCallback, useRef, useState } from "react"
import { useTranslations } from "next-intl"
import { GLYPH_CANVAS, GLYPH_STROKE_WIDTH, GLYPH_VIEWBOX } from "@/lib/config"
import type { MarkStroke } from "@/lib/types"
import { simplifyPath, pointsToSvgPath, type Point } from "@/lib/utils/simplify-path"
import { StrokeRenderer } from "@/components/carve/StrokeRenderer"
import { ActiveStroke } from "@/components/carve/ActiveStroke"

const RDP_EPSILON = 1.5

type DrawingCanvasProps = {
  strokes: MarkStroke[]
  onStrokeComplete: (stroke: MarkStroke) => void
}

const clamp = (v: number) => Math.max(0, Math.min(GLYPH_CANVAS, v))

export function DrawingCanvas({ strokes, onStrokeComplete }: DrawingCanvasProps) {
  const svgRef = useRef<SVGSVGElement>(null)
  const pointsRef = useRef<Point[]>([])
  const drawingRef = useRef(false)
  const rafRef = useRef<number>(0)
  const [activePoints, setActivePoints] = useState<Point[]>([])
  const t = useTranslations("carve")

  // Map a pointer event into the 200×200 glyph coordinate space, clamped to the
  // canvas so strokes never spill past the square (mirrors iOS clamping).
  const toSvgCoords = useCallback((e: React.PointerEvent): Point | null => {
    const svg = svgRef.current
    if (!svg) return null
    const ctm = svg.getScreenCTM()
    if (!ctm) return null
    const inv = ctm.inverse()
    return {
      x: clamp(Math.round((e.clientX * inv.a + e.clientY * inv.c + inv.e) * 100) / 100),
      y: clamp(Math.round((e.clientX * inv.b + e.clientY * inv.d + inv.f) * 100) / 100),
      pressure: e.pressure,
    }
  }, [])

  const handlePointerDown = useCallback((e: React.PointerEvent) => {
    if (e.button !== 0) return
    e.preventDefault()
    ;(e.target as Element).setPointerCapture(e.pointerId)
    drawingRef.current = true
    const pt = toSvgCoords(e)
    if (pt) {
      pointsRef.current = [pt]
      setActivePoints([pt])
    }
  }, [toSvgCoords])

  const handlePointerMove = useCallback((e: React.PointerEvent) => {
    if (!drawingRef.current) return
    e.preventDefault()
    const pt = toSvgCoords(e)
    if (!pt) return
    pointsRef.current.push(pt)

    cancelAnimationFrame(rafRef.current)
    rafRef.current = requestAnimationFrame(() => {
      setActivePoints([...pointsRef.current])
    })
  }, [toSvgCoords])

  const handlePointerUp = useCallback((e: React.PointerEvent) => {
    if (!drawingRef.current) return
    e.preventDefault()
    drawingRef.current = false
    cancelAnimationFrame(rafRef.current)

    const raw = pointsRef.current
    if (raw.length < 2) {
      pointsRef.current = []
      setActivePoints([])
      return
    }

    const simplified = simplifyPath(raw, RDP_EPSILON)
    const d = pointsToSvgPath(simplified)

    // Stroke width is a constant 6 in glyph space (#278) — no pressure variation.
    onStrokeComplete({ d, width: GLYPH_STROKE_WIDTH })
    pointsRef.current = []
    setActivePoints([])
  }, [onStrokeComplete])

  return (
    <svg
      ref={svgRef}
      viewBox={GLYPH_VIEWBOX}
      className="w-full max-w-[300px] aspect-square cursor-crosshair rounded-2xl border border-border bg-card"
      style={{ touchAction: "none" }}
      onPointerDown={handlePointerDown}
      onPointerMove={handlePointerMove}
      onPointerUp={handlePointerUp}
      onPointerCancel={handlePointerUp}
      role="img"
      aria-label={t("canvasAria")}
    >
      <StrokeRenderer strokes={strokes} />
      <ActiveStroke points={activePoints} width={GLYPH_STROKE_WIDTH} />
    </svg>
  )
}
