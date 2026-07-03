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
  /** When set, the whole row becomes a button. */
  onClick?: () => void
  /** Accessible label for interactive rows whose content is not descriptive. */
  ariaLabel?: string
  className?: string
}

const INNER =
  "flex w-full items-center gap-3 px-4 py-3 text-left first:rounded-t-xl last:rounded-b-xl"
const INTERACTIVE = "transition-colors hover:bg-muted/50"

/**
 * A single row inside a `SettingsGroup`. Renders as a link (`href`), a button
 * (`onClick`), or a static container. The web analog of the iOS
 * `.pebblesListRow`. Layout: optional leading icon, flexible main content, and
 * an optional trailing control.
 */
export function SettingsRow({
  icon: Icon,
  children,
  trailing,
  href,
  onClick,
  ariaLabel,
  className,
}: SettingsRowProps) {
  const content = (
    <>
      {Icon ? <Icon className="size-5 shrink-0 text-muted-foreground" aria-hidden /> : null}
      <span className="min-w-0 flex-1 text-sm font-medium">{children}</span>
      {trailing}
    </>
  )

  let inner: ReactNode
  if (href) {
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
