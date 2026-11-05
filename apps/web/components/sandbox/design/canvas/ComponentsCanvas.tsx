"use client"

import { Badge } from "@/components/ui/badge"
import { SPECIMEN_GROUPS, SPECIMENS, type SpecimenEntry } from "@/lib/design/specimens"
import { DesignSection } from "../DesignSection"
import { Artboards } from "./Artboards"

const ENTRIES: readonly SpecimenEntry[] = SPECIMENS

const SECTIONS = [
  {
    id: "primitives",
    title: "Primitives",
    description: "Every file in components/ui, in the states that change its colours. Overlays open from their trigger, inside their screen.",
  },
  {
    id: "components",
    title: "Components",
    description: "The most-used feature components, fed by the fixture store. Links are inert.",
  },
] as const

/**
 * Every specimen, one group per pair of screens: the group renders in a
 * mobile and a desktop frame at their real widths, scaled to the page, so
 * related parts sit side by side and a breakpoint shows as a difference
 * between the two.
 */
export function ComponentsCanvas() {
  return (
    <>
      {SECTIONS.map((section) => (
        <DesignSection key={section.id} id={section.id} title={section.title} description={section.description}>
          {SPECIMEN_GROUPS.filter((g) => g.section === section.id).map((group) => {
            const members = ENTRIES.filter((s) => s.group === group.id)
            return (
              <article key={group.id} id={`group-${group.id}`} aria-labelledby={`group-${group.id}-title`} className="flex scroll-mt-32 flex-col gap-3">
                <header className="flex flex-col gap-2">
                  <h3 id={`group-${group.id}-title`} className="font-heading text-xl">
                    {group.title}
                  </h3>
                  <ul className="flex flex-wrap gap-1.5">
                    {members.map((s) => (
                      <li key={s.id}>
                        <Badge variant={s.m3 === "tuned" ? "default" : "outline"} title={`${s.source} · ${s.m3}`}>
                          {s.name}
                        </Badge>
                      </li>
                    ))}
                  </ul>
                </header>
                <Artboards
                  target={{ specimens: members.map((s) => s.id) }}
                  fit={"fullHeight" in group && group.fullHeight ? "device" : "content"}
                  title={group.title}
                />
              </article>
            )
          })}
        </DesignSection>
      ))}
    </>
  )
}
