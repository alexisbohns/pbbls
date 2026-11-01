import type { ReactNode } from "react"
import Link from "next/link"
import { cn } from "@/lib/utils"

type ListItemProps = {
  headline: ReactNode
  supporting?: ReactNode
  /** An icon, a stone or a glyph, decorative unless it says otherwise. */
  leading?: ReactNode
  /** A value or a count, inside the item. */
  trailing?: ReactNode
  /** A control of its own beside a linked item (remove, a menu): outside the link, never nested in it. */
  action?: ReactNode
  /** The whole item is a link to here. */
  href?: string
  /** The whole item is a button. */
  onClick?: () => void
  /** When the visible words are not the whole name. */
  "aria-label"?: string
  className?: string
}

const BOX = "flex min-h-14 w-full items-center gap-4 bg-m3-surface-container px-4 py-2 text-left"
const INTERACTIVE =
  "outline-none transition-colors hover:bg-m3-surface-container-high focus-visible:ring-2 focus-visible:ring-m3-primary focus-visible:ring-inset motion-reduce:transition-none"

/**
 * An M3 list item (#1384, two-area spec D6, D7), one `<li>` of an
 * `m3-list-group`: a leading slot, the headline over its supporting line, a
 * trailing slot. The whole item is the link or the button, at least 56 px tall.
 */
export function ListItem({ headline, supporting, leading, trailing, action, href, onClick, className, ...aria }: ListItemProps) {
  const content = (
    <>
      {leading && <span className="flex shrink-0 items-center text-m3-on-surface-variant">{leading}</span>}
      <span className="min-w-0 flex-1">
        <span className="m3-type-body-large block text-m3-on-surface">{headline}</span>
        {supporting && <span className="m3-type-body-medium block text-m3-on-surface-variant">{supporting}</span>}
      </span>
      {trailing && <span className="flex shrink-0 items-center text-m3-on-surface-variant">{trailing}</span>}
    </>
  )
  if (action && href) {
    return (
      <li>
        <div className="flex items-center bg-m3-surface-container pe-1">
          <Link href={href} aria-label={aria["aria-label"]} className={cn(BOX, INTERACTIVE, "min-w-0 flex-1 bg-transparent", className)}>
            {content}
          </Link>
          {action}
        </div>
      </li>
    )
  }
  return (
    <li>
      {href ? (
        <Link href={href} aria-label={aria["aria-label"]} className={cn(BOX, INTERACTIVE, className)}>
          {content}
        </Link>
      ) : onClick ? (
        <button type="button" onClick={onClick} aria-label={aria["aria-label"]} className={cn(BOX, INTERACTIVE, className)}>
          {content}
        </button>
      ) : (
        <div className={cn(BOX, className)}>{content}</div>
      )}
    </li>
  )
}
