import type { MouseEvent } from "react"

/**
 * Specimens contain real links (cards, tiles, headers). Capture-phase
 * preventDefault stops the browser; stopPropagation stops next/link's handler.
 * React events follow the React tree, so this covers portaled overlays too.
 * A fragment link never leaves the page, and the design directory's own links
 * (its tabs, a frame's 1:1 view) are the way around it, so both pass.
 */
export function blockLinkNavigation(e: MouseEvent) {
  const link = e.target instanceof Element ? e.target.closest("a[href]") : null
  const href = link?.getAttribute("href") ?? ""
  if (link && !href.startsWith("#") && !href.startsWith("/sandbox/design")) {
    e.preventDefault()
    e.stopPropagation()
  }
}
