import type { ReactNode } from "react"
import { Badge } from "@/components/ui/badge"
import { getSpecimen, type SpecimenId } from "@/lib/design/specimens"
import { cn } from "@/lib/utils"

type SpecimenProps = {
  id: SpecimenId
  /** A known limitation of rendering this specimen from fixtures. */
  note?: string
  className?: string
  children: ReactNode
}

/** One registry entry on the design page: name, source, M3 status, then the rendered states. */
export function Specimen({ id, note, className, children }: SpecimenProps) {
  const entry = getSpecimen(id)
  return (
    <article id={`specimen-${id}`} aria-labelledby={`specimen-${id}-title`} className="flex flex-col gap-3">
      <header className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
        <h3 id={`specimen-${id}-title`} className="font-heading text-xl">
          {entry.name}
        </h3>
        <code className="text-xs text-muted-foreground">{entry.source}</code>
        <Badge variant={entry.m3 === "tuned" ? "default" : "outline"}>{entry.m3}</Badge>
      </header>
      {note && <p className="max-w-2xl text-sm text-muted-foreground">{note}</p>}
      <div
        className={cn(
          "flex flex-wrap items-start gap-4 rounded-xl border border-dashed border-border p-4",
          className,
        )}
      >
        {children}
      </div>
    </article>
  )
}
