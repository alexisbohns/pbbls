"use client"

import { useCallback, useRef, useState } from "react"
import type { PebbleShape } from "@/lib/config"
import type { MarkStroke } from "@/lib/types"
import { simplifyPath, pointsToSvgPath, type Point } from "@/lib/utils/simplify-path"
import { PebbleOutline } from "@/components/carve/PebbleOutline"
import { StrokeRenderer } from "@/components/carve/StrokeRenderer"
import { ActiveStroke } from "@/components/carve/ActiveStroke"

const RDP_EPSILON = 1.5
const PRESSURE_MULTIPLIER = 4

type DrawingCanvasProps = {
  shape: PebbleShape
  strokes: MarkStroke[]
  strokeWidth: number
  onStrokeComplete: (stroke: MarkStroke) => void
}

export function DrawingCanvas({
  shape,
  strokes,
  strokeWidth,
  onStrokeComplete,
}: DrawingCanvasProps) {
  const svgRef = useRef<SVGSVGElement>(null)
  const pointsRef = useRef<Point[]>([])
  const drawingRef = useRef(false)
  const rafRef = useRef<number>(0)
  const [activePoints, setActivePoints] = useState<Point[]>([])

  const toSvgCoords = useCallback((e: React.PointerEvent): Point | null => {
    const svg = svgRef.current
    if (!svg) return null
    const ctm = svg.getScreenCTM()
    if (!ctm) return null
    const inv = ctm.inverse()
    return {
      x: Math.round((e.clientX * inv.a + e.clientY * inv.c + inv.e) * 100) / 100,
      y: Math.round((e.clientX * inv.b + e.clientY * inv.d + inv.f) * 100) / 100,
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

    // Compute effective width from average pressure
    const avgPressure = raw.reduce((sum, p) => sum + (p.pressure ?? 0.5), 0) / raw.length
    const effectiveWidth = strokeWidth + avgPressure * PRESSURE_MULTIPLIER

    onStrokeComplete({ d, width: Math.round(effectiveWidth * 10) / 10 })
    pointsRef.current = []
    setActivePoints([])
  }, [strokeWidth, onStrokeComplete])

  const clipId = `pebble-clip-${shape.id}`

  return (
    <svg
      ref={svgRef}
      viewBox={shape.viewBox}
      className="w-full max-w-[300px] aspect-square cursor-crosshair"
      style={{ touchAction: "none" }}
      onPointerDown={handlePointerDown}
      onPointerMove={handlePointerMove}
      onPointerUp={handlePointerUp}
      onPointerCancel={handlePointerUp}
      role="img"
      aria-label="Drawing canvas for pebble mark"
    >
      <PebbleOutline shape={shape} clipId={clipId} />
      <g clipPath={`url(#${clipId})`}>
        <StrokeRenderer strokes={strokes} />
        <ActiveStroke points={activePoints} width={strokeWidth} />
      </g>
    </svg>
  )
}
