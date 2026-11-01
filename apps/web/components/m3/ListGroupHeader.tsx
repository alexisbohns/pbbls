/**
 * An M3 list group's header (#1384, two-area spec D7, as Android #1437): sentence
 * case in `primary`, `label-large`, an `h3` (inside a detail, the detail's title
 * is the `h2`). Give it an id and label the group with it.
 */
export function ListGroupHeader({ id, children }: { id: string; children: React.ReactNode }) {
  return (
    <h3 id={id} className="m3-type-label-large px-4 pb-1 text-m3-primary">
      {children}
    </h3>
  )
}
