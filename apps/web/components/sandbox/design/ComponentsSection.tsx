"use client"

import { Flame, Gem, Leaf, Settings, Sparkles, UserRound } from "lucide-react"
import { Button } from "@/components/ui/button"
import { PageHeader } from "@/components/layout/PageHeader"
import { EmptyState } from "@/components/layout/EmptyState"
import { PebbleCard } from "@/components/path/PebbleCard"
import { PathPebbleRow } from "@/components/path/PathPebbleRow"
import { PathEmptyState } from "@/components/path/PathEmptyState"
import { GamificationBlock } from "@/components/path/GamificationBlock"
import { PebbleVisual } from "@/components/pebble/PebbleVisual"
import { IntensityDots, PositivenessIndicator } from "@/components/pebble/PebbleIndicators"
import { SoulCard } from "@/components/souls/SoulCard"
import { SoulsEmptyState } from "@/components/souls/SoulsEmptyState"
import { CollectionCard } from "@/components/collections/CollectionCard"
import { ModeBadge } from "@/components/collections/ModeBadge"
import { ProfileBanner } from "@/components/profile/ProfileBanner"
import { StatsCard } from "@/components/profile/StatsCard"
import { DataTile } from "@/components/profile/DataTile"
import { ShortcutTile } from "@/components/profile/ShortcutTile"
import { SectionCard } from "@/components/profile/SectionCard"
import { LabCard } from "@/components/profile/LabCard"
import { SettingsGroup } from "@/components/settings/SettingsGroup"
import { SettingsRow } from "@/components/settings/SettingsRow"
import { EMOTIONS } from "@/lib/config/emotions"
import { useCollections } from "@/lib/data/useCollections"
import { usePebbles } from "@/lib/data/usePebbles"
import { DESIGN_ASSIDUITY, DESIGN_PROFILE, DESIGN_RIPPLE } from "@/lib/seed/design-fixtures"
import { SANDBOX_MARK_MAP, SANDBOX_SOULS } from "@/lib/seed/sandbox-pebbles"
import { DesignSection } from "./DesignSection"
import { Specimen } from "./Specimen"

function markOf(id: string | undefined | null) {
  return id ? SANDBOX_MARK_MAP.get(id) : undefined
}

function soulNames(ids: string[]): string[] {
  return SANDBOX_SOULS.filter((s) => ids.includes(s.id)).map((s) => s.name)
}

export function ComponentsSection() {
  const { pebbles } = usePebbles()
  const { collections } = useCollections()

  return (
    <DesignSection
      id="components"
      title="Components"
      description="The most-used feature components, fed by the fixture store. Links are inert on this page."
    >
      <Specimen id="pebble-card">
        {pebbles.slice(0, 2).map((pebble) => (
          <div key={pebble.id} className="w-80">
            <PebbleCard
              pebble={pebble}
              emotion={EMOTIONS.find((e) => e.id === pebble.emotion_id)}
              mark={markOf(pebble.mark_id)}
              soulNames={soulNames(pebble.soul_ids)}
            />
          </div>
        ))}
      </Specimen>

      <Specimen id="path-pebble-row">
        <div className="flex w-full max-w-md flex-col">
          {pebbles.slice(0, 3).map((pebble, i) => (
            <PathPebbleRow key={pebble.id} pebble={pebble} mark={markOf(pebble.mark_id)} positionIndex={i} />
          ))}
        </div>
      </Specimen>

      <Specimen id="path-empty-state">
        <div className="w-full max-w-md">
          <PathEmptyState />
        </div>
      </Specimen>

      <Specimen id="gamification-block">
        <GamificationBlock
          icon={Flame}
          label="Bounce"
          value={3}
          dialogTitle="Bounce"
          dialogDescription="Days in a row with at least one pebble."
        />
        <GamificationBlock
          icon={Gem}
          label="Karma"
          value={42}
          dialogTitle="Karma"
          dialogDescription="Earned by recording and caring for pebbles."
        />
      </Specimen>

      <Specimen id="pebble-visual">
        {pebbles.slice(0, 4).map((pebble) => (
          <div key={pebble.id} className="size-24">
            <PebbleVisual pebble={pebble} mark={markOf(pebble.mark_id)} />
          </div>
        ))}
      </Specimen>

      <Specimen id="pebble-indicators">
        {([1, 2, 3] as const).map((intensity) => (
          <IntensityDots key={intensity} intensity={intensity} />
        ))}
        {[-1, 0, 1].map((value) => (
          <PositivenessIndicator key={value} value={value} />
        ))}
      </Specimen>

      <Specimen id="soul-card">
        {SANDBOX_SOULS.slice(0, 3).map((soul) => (
          <div key={soul.id} className="w-60">
            <SoulCard
              soul={soul}
              mark={markOf(soul.glyph_id)}
              pebbleCount={pebbles.filter((p) => p.soul_ids.includes(soul.id)).length}
            />
          </div>
        ))}
      </Specimen>

      <Specimen id="souls-empty-state">
        <div className="w-full max-w-md">
          <SoulsEmptyState />
        </div>
      </Specimen>

      <Specimen id="collection-card">
        {collections.map((collection) => (
          <div key={collection.id} className="w-72">
            <CollectionCard collection={collection} />
          </div>
        ))}
      </Specimen>

      <Specimen id="mode-badge">
        <ModeBadge mode="stack" />
        <ModeBadge mode="pack" />
        <ModeBadge mode="track" />
      </Specimen>

      <Specimen id="profile-banner">
        <div className="w-full max-w-md">
          <ProfileBanner
            displayName={DESIGN_PROFILE.display_name}
            memberSince="Nov 2, 2025"
            glyph={markOf(DESIGN_PROFILE.glyph_id) ?? null}
          />
        </div>
      </Specimen>

      <Specimen id="stats-card">
        <div className="w-full max-w-md">
          <StatsCard
            ripple={DESIGN_RIPPLE}
            assiduity={DESIGN_ASSIDUITY}
            daysPracticed={19}
            pebbles={pebbles.length}
            karma={42}
          />
        </div>
      </Specimen>

      <Specimen id="data-tile">
        <DataTile value={128} icon={Sparkles} label="Pebbles" />
        <DataTile value={null} icon={Leaf} label="Loading" />
      </Specimen>

      <Specimen id="shortcut-tile">
        <ShortcutTile href="/souls" icon={UserRound} label="Souls" />
        <ShortcutTile href="/settings" icon={Settings} label="Settings" />
      </Specimen>

      <Specimen id="section-card">
        <SectionCard className="w-80">
          <p className="text-sm">Content in a section card.</p>
        </SectionCard>
      </Specimen>

      <Specimen id="lab-card">
        <div className="w-full max-w-md">
          <LabCard />
        </div>
      </Specimen>

      <Specimen id="settings-row">
        <div className="w-full max-w-md">
          <SettingsGroup aria-label="Example settings group">
            <SettingsRow icon={UserRound} href="/settings">
              Linked row
            </SettingsRow>
            <SettingsRow icon={Leaf} trailing={<span className="text-sm text-muted-foreground">On</span>}>
              Row with a trailing value
            </SettingsRow>
            <SettingsRow onClick={() => {}}>Plain row</SettingsRow>
          </SettingsGroup>
        </div>
      </Specimen>

      <Specimen id="page-header">
        <div className="w-full max-w-md">
          <PageHeader title="Page title" backHref="/profile" rightSlot={<Button size="sm">Save</Button>} />
        </div>
      </Specimen>

      <Specimen id="empty-state">
        <div className="w-full max-w-md">
          <EmptyState
            title="Nothing here yet"
            description="Empty states say what will appear here and offer the next step."
            action={<Button>Do the thing</Button>}
          />
        </div>
      </Specimen>
    </DesignSection>
  )
}
