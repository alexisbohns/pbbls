import type { ComponentType, ReactNode } from "react"
import Link from "next/link"
import { cn } from "@/lib/utils"

type IconType = ComponentType<{ className?: string; "aria-hidden"?: boolean }>

type SettingsRowProps = {
  /** Optional leading icon (Lucide). */
  icon?: IconType
  /** Main content — a label, value, or an inline control (e.g. an input). */
  children: ReactNode
  /** Right-aligned slot — a chevron, toggle, or switcher. */
  trailing?: ReactNode
  /** When set, the whole row becomes a link. */
  href?: string
  /** With `href`: an absolute URL — `https` opens in a new tab, `mailto:` in the mail app. */
  external?: boolean
  /** When set, the whole row becomes a button. */
  onClick?: () => void
  /** Accessible label for interactive rows whose content is not descriptive. */
  ariaLabel?: string
  className?: string
}

const INNER = "m3-type-body-large flex min-h-14 w-full items-center gap-4 bg-m3-surface-container px-4 py-2 text-left text-m3-on-surface"
const INTERACTIVE =
  "outline-none transition-colors hover:bg-m3-surface-container-high focus-visible:ring-2 focus-visible:ring-m3-primary focus-visible:ring-inset motion-reduce:transition-none"

/**
 * A single row inside a `SettingsGroup`: an M3 list item since #1384 (two-area
 * spec D7). Renders as a link (`href`), a button (`onClick`), or a static
 * container. Layout: optional leading icon, flexible main content, and
 * an optional trailing control.
 */
export function SettingsRow({
  icon: Icon,
  children,
  trailing,
  href,
  external,
  onClick,
  ariaLabel,
  className,
}: SettingsRowProps) {
  const content = (
    <>
      {Icon ? <Icon className="size-5 shrink-0 text-m3-on-surface-variant" aria-hidden /> : null}
      <span className="min-w-0 flex-1">{children}</span>
      {trailing}
    </>
  )

  let inner: ReactNode
  if (href && external) {
    const newTab = href.startsWith("https://")
    inner = (
      <a
        href={href}
        aria-label={ariaLabel}
        className={cn(INNER, INTERACTIVE)}
        {...(newTab ? { target: "_blank", rel: "noopener noreferrer" } : {})}
      >
        {content}
      </a>
    )
  } else if (href) {
    inner = (
      <Link href={href} aria-label={ariaLabel} className={cn(INNER, INTERACTIVE)}>
        {content}
      </Link>
    )
  } else if (onClick) {
    inner = (
      <button type="button" onClick={onClick} aria-label={ariaLabel} className={cn(INNER, INTERACTIVE)}>
        {content}
      </button>
    )
  } else {
    inner = <div className={INNER}>{content}</div>
  }

  return <li className={className}>{inner}</li>
}
