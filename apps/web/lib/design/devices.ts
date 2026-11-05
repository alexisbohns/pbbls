/**
 * The screens /sandbox/design draws every view and component group on. Each
 * renders in an iframe of the device's real size, so media queries (the
 * 1024 px two-area breakpoint, `md:`) see a phone or a desktop, then the frame
 * is scaled down to fit the page. Add a device here and every canvas shows it.
 */

export interface DesignDevice {
  readonly id: "mobile" | "desktop"
  readonly label: string
  /** CSS pixels, the iframe's viewport. */
  readonly width: number
  readonly height: number
}

export const DESIGN_DEVICES: readonly DesignDevice[] = [
  { id: "mobile", label: "Mobile", width: 390, height: 844 },
  { id: "desktop", label: "Desktop", width: 1440, height: 900 },
]

export type DeviceChoice = "both" | DesignDevice["id"]

export function devicesFor(choice: DeviceChoice): readonly DesignDevice[] {
  return choice === "both" ? DESIGN_DEVICES : DESIGN_DEVICES.filter((d) => d.id === choice)
}

/** Fit to the column ("fit") or a fixed ratio of the real size. */
export type CanvasZoom = "fit" | 0.25 | 0.5 | 0.75 | 1

export const CANVAS_ZOOMS: readonly CanvasZoom[] = ["fit", 0.25, 0.5, 0.75, 1]

/** The gap between two frames in a row, in page pixels. */
export const FRAME_GAP = 24

/**
 * One scale for every frame in a row, so a phone and a desktop keep their
 * real proportions to each other: the largest that lays them side by side in
 * `available` page pixels, never above 1:1.
 */
export function fitScale(devices: readonly DesignDevice[], available: number): number {
  if (devices.length === 0 || available <= 0) return 1
  const total = devices.reduce((sum, d) => sum + d.width, 0)
  const room = available - FRAME_GAP * (devices.length - 1)
  return Math.max(0.1, Math.min(1, room / total))
}

export function scaleFor(zoom: CanvasZoom, devices: readonly DesignDevice[], available: number): number {
  return zoom === "fit" ? fitScale(devices, available) : zoom
}
