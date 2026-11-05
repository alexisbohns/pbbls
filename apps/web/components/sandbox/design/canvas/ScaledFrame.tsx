"use client"

import { useEffect, useRef, useState, type RefObject } from "react"
import type { DesignDevice } from "@/lib/design/devices"
import { frameHref, frameMessage, parseFrameMessage, type FrameTarget } from "@/lib/design/frame-protocol"
import { useDesign } from "../DesignContext"

/** The nearest scrolling ancestor: the app's <main>, whose clip an IntersectionObserver on the viewport would ignore margins past. */
function scrollParent(el: HTMLElement): HTMLElement | null {
  for (let node = el.parentElement; node; node = node.parentElement) {
    const { overflowY } = getComputedStyle(node)
    if (overflowY === "auto" || overflowY === "scroll") return node
  }
  return null
}

/**
 * Mounts a frame when it comes within a screen of the visible area and drops
 * it once it is three screens away, so a long canvas keeps a handful of live
 * iframes instead of dozens.
 */
function useNearViewport(ref: RefObject<HTMLElement | null>): boolean {
  const [near, setNear] = useState(false)
  useEffect(() => {
    const el = ref.current
    if (!el) return
    const root = scrollParent(el)
    const enter = new IntersectionObserver(([entry]) => entry.isIntersecting && setNear(true), {
      root,
      rootMargin: "100% 0px",
    })
    const leave = new IntersectionObserver(([entry]) => !entry.isIntersecting && setNear(false), {
      root,
      rootMargin: "300% 0px",
    })
    enter.observe(el)
    leave.observe(el)
    return () => {
      enter.disconnect()
      leave.disconnect()
    }
  }, [ref])
  return near
}

type LiveFrameProps = {
  src: string
  title: string
  themeKey: string
  width: number
  height: number
  scale: number
  onHeight: (height: number) => void
}

function LiveFrame({ src, title, themeKey, width, height, scale, onHeight }: LiveFrameProps) {
  const ref = useRef<HTMLIFrameElement>(null)
  // The theme the frame loads with; later changes go by message, not reload.
  const [initialSrc] = useState(src)
  const themeRef = useRef(themeKey)

  useEffect(() => {
    themeRef.current = themeKey
    ref.current?.contentWindow?.postMessage(frameMessage({ type: "theme", themeKey }), window.location.origin)
  }, [themeKey])

  useEffect(() => {
    function onMessage(e: MessageEvent) {
      const frame = ref.current?.contentWindow
      if (!frame || e.origin !== window.location.origin || e.source !== frame) return
      const message = parseFrameMessage(e.data)
      if (message?.type === "ready") {
        frame.postMessage(frameMessage({ type: "theme", themeKey: themeRef.current }), window.location.origin)
      } else if (message?.type === "height") {
        onHeight(message.height)
      }
    }
    window.addEventListener("message", onMessage)
    return () => window.removeEventListener("message", onMessage)
  }, [onHeight])

  return (
    <iframe
      ref={ref}
      src={initialSrc}
      title={title}
      className="absolute top-0 left-0 origin-top-left border-0"
      style={{ width, height, transform: `scale(${scale})` }}
    />
  )
}

type ScaledFrameProps = {
  device: DesignDevice
  target: FrameTarget
  scale: number
  /** "device": the screen's height, scrolled inside. "content": as tall as what it draws. */
  fit: "device" | "content"
  title: string
}

/**
 * One screen of the canvas: `target` rendered in an iframe of the device's
 * real size, then scaled, so a phone and a desktop drawn at the same scale
 * keep their true proportions and each sees its own breakpoints.
 */
export function ScaledFrame({ device, target, scale, fit, title }: ScaledFrameProps) {
  const { themeKey } = useDesign()
  const boxRef = useRef<HTMLDivElement>(null)
  const near = useNearViewport(boxRef)
  const [contentHeight, setContentHeight] = useState<number | null>(null)
  const height = fit === "device" ? device.height : Math.max(contentHeight ?? 480, 80)
  const href = frameHref(target, themeKey)

  return (
    <figure className="flex shrink-0 flex-col gap-1.5" style={{ width: device.width * scale }}>
      <figcaption className="flex items-baseline justify-between gap-2 font-mono text-[11px] whitespace-nowrap text-muted-foreground">
        <span className="min-w-0 truncate" title={`${device.label} · ${device.width}×${Math.round(height)} at ${Math.round(scale * 100)}%`}>
          {device.label} · {device.width}×{Math.round(height)} · {Math.round(scale * 100)}%
        </span>
        <a href={href} target="_blank" rel="noopener" className="shrink-0 underline-offset-2 hover:underline" title="Open this screen at its real size">
          1:1
        </a>
      </figcaption>
      <div
        ref={boxRef}
        className="relative overflow-hidden rounded-xl border border-border bg-background shadow-sm"
        style={{ width: device.width * scale, height: height * scale }}
      >
        {near ? (
          <LiveFrame
            src={href}
            title={`${title} · ${device.label}`}
            themeKey={themeKey}
            width={device.width}
            height={height}
            scale={scale}
            onHeight={setContentHeight}
          />
        ) : (
          <div className="flex h-full items-center justify-center text-xs text-muted-foreground">{device.label}</div>
        )}
      </div>
    </figure>
  )
}
