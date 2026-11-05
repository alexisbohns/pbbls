/**
 * How /sandbox/design talks to the frames it draws (`/sandbox/design/frame`).
 * Same origin only. The frame keeps its own URL as the source of its theme, so
 * a frame opened on its own (the "1:1" link) renders exactly what the canvas
 * shows; the canvas pushes theme changes instead of reloading every frame.
 */

export const FRAME_PATH = "/sandbox/design/frame"

const SOURCE = "pbbls-design"

/** What a frame renders: one view, or a set of specimens from the registry. */
export type FrameTarget = { view: string } | { specimens: readonly string[] }

export type FrameMessage =
  /** Frame → canvas: listening; send the current theme. */
  | { source: typeof SOURCE; type: "ready" }
  /** Frame → canvas: its content's height, for frames that grow with their content. */
  | { source: typeof SOURCE; type: "height"; height: number }
  /** Canvas → frame: the theme changed (a serialized DesignTheme). */
  | { source: typeof SOURCE; type: "theme"; themeKey: string }

export function frameMessage(
  message: { type: "ready" } | { type: "height"; height: number } | { type: "theme"; themeKey: string },
): FrameMessage {
  return { source: SOURCE, ...message }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null
}

/** The message, or null for anything another script posted. */
export function parseFrameMessage(data: unknown): FrameMessage | null {
  if (!isRecord(data) || data.source !== SOURCE) return null
  if (data.type === "ready") return { source: SOURCE, type: "ready" }
  if (data.type === "height" && typeof data.height === "number" && Number.isFinite(data.height)) {
    return { source: SOURCE, type: "height", height: data.height }
  }
  if (data.type === "theme" && typeof data.themeKey === "string") {
    return { source: SOURCE, type: "theme", themeKey: data.themeKey }
  }
  return null
}

/** The frame's own params: its target. */
export function targetParams(target: FrameTarget): URLSearchParams {
  return "view" in target
    ? new URLSearchParams({ view: target.view })
    : new URLSearchParams({ specimens: target.specimens.join(",") })
}

export function parseFrameTarget(params: Pick<URLSearchParams, "get">): FrameTarget | null {
  const view = params.get("view")
  if (view) return { view }
  const specimens = params.get("specimens")?.split(",").filter(Boolean)
  return specimens && specimens.length > 0 ? { specimens } : null
}

/** `/sandbox/design/frame?<target>&<theme>`. */
export function frameHref(target: FrameTarget, themeKey: string): string {
  const params = targetParams(target)
  for (const [key, value] of new URLSearchParams(themeKey)) params.set(key, value)
  return `${FRAME_PATH}?${params.toString()}`
}
