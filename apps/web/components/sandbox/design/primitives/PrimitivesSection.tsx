"use client"

import { useState } from "react"
import { Plus } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Calendar } from "@/components/ui/calendar"
import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Checkbox } from "@/components/ui/checkbox"
import { Input } from "@/components/ui/input"
import { EmotionBadge } from "@/components/ui/EmotionBadge"
import { SearchableList } from "@/components/ui/SearchableList"
import { SectionLabel } from "@/components/ui/SectionLabel"
import { SelectableItem } from "@/components/ui/SelectableItem"
import { TagList } from "@/components/ui/TagList"
import { EMOTIONS } from "@/lib/config/emotions"
import { SANDBOX_SOULS } from "@/lib/seed/sandbox-pebbles"
import { DesignSection } from "../DesignSection"
import { Specimen } from "../Specimen"
import { SpecimenState } from "../SpecimenState"
import { OverlaySpecimens } from "./OverlaySpecimens"

const BUTTON_VARIANTS = ["default", "outline", "secondary", "ghost", "destructive", "link"] as const
const BUTTON_SIZES = ["xs", "sm", "default", "lg"] as const
const ICON_SIZES = ["icon-xs", "icon-sm", "icon", "icon-lg"] as const
const BADGE_VARIANTS = ["default", "secondary", "destructive", "outline", "ghost", "link"] as const

export function PrimitivesSection() {
  const [day, setDay] = useState<Date | undefined>(() => new Date())
  const [query, setQuery] = useState("")
  const [selected, setSelected] = useState(SANDBOX_SOULS[1].id)
  const matches = SANDBOX_SOULS.filter((s) => s.name.toLowerCase().includes(query.toLowerCase()))

  return (
    <DesignSection
      id="primitives"
      title="Primitives"
      description="Every file in components/ui, in the states that change its colours. Overlays open from their trigger."
    >
      <Specimen id="button">
        <div className="flex flex-col gap-4">
          {BUTTON_VARIANTS.map((variant) => (
            <SpecimenState key={variant} label={variant}>
              <div className="flex flex-wrap items-center gap-2">
                {BUTTON_SIZES.map((size) => (
                  <Button key={size} variant={variant} size={size}>
                    {size}
                  </Button>
                ))}
                {ICON_SIZES.map((size) => (
                  <Button key={size} variant={variant} size={size} aria-label={`${variant} ${size}`}>
                    <Plus />
                  </Button>
                ))}
                <Button variant={variant}>
                  <Plus data-icon="inline-start" />
                  With icon
                </Button>
                <Button variant={variant} disabled>
                  Disabled
                </Button>
              </div>
            </SpecimenState>
          ))}
        </div>
      </Specimen>

      <Specimen id="badge">
        {BADGE_VARIANTS.map((variant) => (
          <Badge key={variant} variant={variant}>
            {variant}
          </Badge>
        ))}
      </Specimen>

      <Specimen id="card">
        <Card className="w-80">
          <CardHeader>
            <CardTitle>Card title</CardTitle>
            <CardDescription>Supporting description text.</CardDescription>
            <CardAction>
              <Button size="sm" variant="ghost">
                Action
              </Button>
            </CardAction>
          </CardHeader>
          <CardContent className="text-sm">Body content sits on the card surface.</CardContent>
          <CardFooter>
            <Button size="sm">Primary</Button>
          </CardFooter>
        </Card>
      </Specimen>

      <Specimen id="checkbox">
        <SpecimenState label="off">
          <Checkbox aria-label="Off" />
        </SpecimenState>
        <SpecimenState label="on">
          <Checkbox aria-label="On" defaultChecked />
        </SpecimenState>
        <SpecimenState label="disabled">
          <Checkbox aria-label="Disabled" disabled />
        </SpecimenState>
        <SpecimenState label="disabled · on">
          <Checkbox aria-label="Disabled on" disabled defaultChecked />
        </SpecimenState>
      </Specimen>

      <Specimen id="input">
        <SpecimenState label="empty">
          <Input className="w-56" placeholder="Name this pebble" />
        </SpecimenState>
        <SpecimenState label="filled">
          <Input className="w-56" defaultValue="Lunch by the canal" />
        </SpecimenState>
        <SpecimenState label="invalid">
          <Input className="w-56" defaultValue="not-an-email" aria-invalid />
        </SpecimenState>
        <SpecimenState label="disabled">
          <Input className="w-56" defaultValue="Can’t touch this" disabled />
        </SpecimenState>
      </Specimen>

      <Specimen id="calendar">
        <Calendar mode="single" selected={day} onSelect={setDay} />
      </Specimen>

      <OverlaySpecimens />

      <Specimen id="emotion-badge">
        {EMOTIONS.slice(0, 6).map((emotion) => (
          <EmotionBadge key={emotion.id} emotion={emotion} />
        ))}
        {EMOTIONS.slice(0, 3).map((emotion) => (
          <EmotionBadge key={`${emotion.id}-md`} emotion={emotion} size="md" />
        ))}
      </Specimen>

      <Specimen id="searchable-list">
        <div className="w-72">
          <SearchableList
            query={query}
            onQueryChange={setQuery}
            placeholder="Search souls"
            isEmpty={matches.length === 0}
            emptyMessage="No soul matches"
          >
            {matches.map((soul) => (
              <SelectableItem key={soul.id} selected={false} onSelect={() => setQuery(soul.name)}>
                {soul.name}
              </SelectableItem>
            ))}
          </SearchableList>
        </div>
      </Specimen>

      <Specimen id="section-label">
        <SectionLabel>Section label</SectionLabel>
      </Specimen>

      <Specimen id="selectable-item">
        <div role="radiogroup" aria-label="Selectable items" className="flex w-72 flex-col gap-1">
          {SANDBOX_SOULS.slice(0, 3).map((soul, i) => (
            <SelectableItem
              key={soul.id}
              role="radio"
              selected={soul.id === selected}
              onSelect={() => setSelected(soul.id)}
              showCheck
              muted={i === 2}
            >
              {soul.name}
              {i === 2 ? " (muted)" : ""}
            </SelectableItem>
          ))}
        </div>
      </Specimen>

      <Specimen id="tag-list">
        <TagList items={SANDBOX_SOULS.map((s) => ({ id: s.id, name: s.name }))} />
      </Specimen>
    </DesignSection>
  )
}
