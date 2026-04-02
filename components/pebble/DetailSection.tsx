import type { ReactNode, ReactElement } from "react"
import { Plus } from "lucide-react"
import { Button } from "@/components/ui/button"

type DetailSectionProps = {
  id: string
  title: string
  children?: ReactNode
  addTrigger?: ReactElement
}

export function DetailSection({ id, title, children, addTrigger }: DetailSectionProps) {
  const headingId = `${id}-heading`

  return (
    <section className="mt-6" aria-labelledby={headingId}>
      <div className="flex items-center justify-between">
        <h2
          id={headingId}
          className="text-xs font-medium uppercase tracking-wider text-muted-foreground"
        >
          {title}
        </h2>
        {addTrigger ?? (
          <Button
            variant="ghost"
            size="icon-xs"
            aria-label={`Add ${title.toLowerCase()}`}
            disabled
          >
            <Plus />
          </Button>
        )}
      </div>
      {children}
    </section>
  )
}
