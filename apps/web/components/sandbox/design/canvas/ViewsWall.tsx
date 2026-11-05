"use client"

import { Badge } from "@/components/ui/badge"
import { SPECIMENS, type SpecimenEntry } from "@/lib/design/specimens"
import { DesignSection } from "../DesignSection"
import { Artboards } from "./Artboards"

const VIEWS: readonly SpecimenEntry[] = SPECIMENS.filter((s) => s.section === "views")

/**
 * Every screen of the app on every device. At "Fit" one view fills a row;
 * zoomed out, the views wrap into a wall to scan the whole app at once.
 */
export function ViewsWall() {
  return (
    <DesignSection
      id="views"
      title="Views"
      description="Whole screens composed the way their app/ pages compose them, fed by the fixture store, each in a real-size phone and desktop viewport (so the 1024 px two-area layout shows on desktop). Scroll inside a screen; writes do nothing and links are inert. 1:1 opens the screen on its own."
    >
      <div className="flex flex-wrap items-start gap-x-10 gap-y-12">
        {VIEWS.map((view) => (
          <article key={view.id} id={view.id} aria-labelledby={`${view.id}-title`} className="flex w-min max-w-full min-w-0 scroll-mt-32 flex-col gap-3">
            <header className="flex flex-col gap-1">
              <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
                <h3 id={`${view.id}-title`} className="font-heading text-xl">
                  {view.name}
                </h3>
                <code className="text-xs text-muted-foreground">{view.source}</code>
                <Badge variant={view.m3 === "tuned" ? "default" : "outline"}>{view.m3}</Badge>
              </div>
              {view.note && <p className="max-w-2xl text-sm text-muted-foreground">{view.note}</p>}
            </header>
            <Artboards target={{ view: view.id }} fit="device" title={view.name} />
          </article>
        ))}
      </div>
    </DesignSection>
  )
}
