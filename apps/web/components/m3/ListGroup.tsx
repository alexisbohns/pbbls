import { useId, type ReactNode } from "react"

/**
 * An M3 list group (#1384, two-area spec D7, as Android #1437): an optional
 * header in `primary`, sentence case, then the items (`ListItem`s) as one
 * segmented group. The header is an `h3`: inside a detail, the detail's
 * title is the `h2`.
 */
export function ListGroup({ title, children, className }: { title?: string; children: ReactNode; className?: string }) {
  const id = useId()
  return (
    <section aria-labelledby={title ? id : undefined} className={className}>
      {title && (
        <h3 id={id} className="m3-type-label-large px-4 pb-2 text-m3-primary">
          {title}
        </h3>
      )}
      <ul className="m3-list-group">{children}</ul>
    </section>
  )
}
