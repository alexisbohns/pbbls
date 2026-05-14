import type { ReactNode } from "react"

type EmptyStateProps = {
  title: string
  description: string
  action?: ReactNode
}

export function EmptyState({ title, description, action }: EmptyStateProps) {
  return (
    <section className="flex flex-col items-center justify-center gap-4 py-20 text-center h-full">
      <div>
        <h2 className="text-lg font-medium mb-0">{title}</h2>
        <p className="text-sm text-muted-foreground">{description}</p>
      </div>
      {action}
    </section>
  )
}
