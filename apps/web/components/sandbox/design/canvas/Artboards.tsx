"use client"

import { devicesFor, scaleFor } from "@/lib/design/devices"
import type { FrameTarget } from "@/lib/design/frame-protocol"
import { useDesign } from "../DesignContext"
import { ScaledFrame } from "./ScaledFrame"

type ArtboardsProps = {
  target: FrameTarget
  fit: "device" | "content"
  title: string
}

/** `target` on every chosen device, side by side at one scale. */
export function Artboards({ target, fit, title }: ArtboardsProps) {
  const { devices: choice, zoom, canvasWidth } = useDesign()
  const devices = devicesFor(choice)
  const scale = scaleFor(zoom, devices, canvasWidth)
  return (
    <div className="flex max-w-full items-start gap-6 overflow-x-auto pb-1">
      {devices.map((device) => (
        <ScaledFrame key={device.id} device={device} target={target} scale={scale} fit={fit} title={title} />
      ))}
    </div>
  )
}
