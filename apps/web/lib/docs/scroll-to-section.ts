import type { MouseEvent } from "react"

/**
 * A contents link's click: glide to the section instead of jumping (straight
 * there when the reader asks for reduced motion), and keep the hash in the
 * address so the place can be shared. The heading's scroll-margin keeps it
 * clear of the sticky bar.
 */
export function scrollToSection(event: MouseEvent<HTMLAnchorElement>, id: string) {
  const target = document.getElementById(id)
  if (!target || event.metaKey || event.ctrlKey || event.shiftKey || event.button !== 0) return
  event.preventDefault()
  const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches
  target.scrollIntoView({ behavior: reduce ? "auto" : "smooth", block: "start" })
  history.replaceState(null, "", `#${id}`)
}
