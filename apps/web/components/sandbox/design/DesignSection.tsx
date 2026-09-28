import type { ReactNode } from "react"

type DesignSectionProps = {
  id: string
  title: string
  description: string
  children: ReactNode
}

export function DesignSection({ id, title, description, children }: DesignSectionProps) {
  return (
    <section id={id} aria-labelledby={`${id}-title`} className="flex scroll-mt-32 flex-col gap-10">
      <header className="flex flex-col gap-1">
        <h2 id={`${id}-title`} className="font-heading text-3xl">
          {title}
        </h2>
        <p className="max-w-2xl text-sm text-muted-foreground">{description}</p>
      </header>
      {children}
    </section>
  )
}
