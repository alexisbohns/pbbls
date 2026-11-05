"use client"

import type { ReactNode } from "react"
import type { SpecimenId } from "@/lib/design/specimens"
import { AchievementSheetOpenView } from "./AchievementSheetOpenView"
import { AchievementsOpenView } from "./AchievementsOpenView"
import { CollectionOpenView } from "./CollectionOpenView"
import { CollectionsView } from "./CollectionsView"
import { GlyphsOpenView } from "./GlyphsOpenView"
import { GuideOpenView } from "./GuideOpenView"
import { HomeView } from "./HomeView"
import { LabOpenView } from "./LabOpenView"
import { PathView } from "./PathView"
import { PebbleDetailView } from "./PebbleDetailView"
import { PeopleView } from "./PeopleView"
import { ProfileView } from "./ProfileView"
import { SettingsView } from "./SettingsView"
import { SoulOpenView } from "./SoulOpenView"
import { WalletOpenView } from "./WalletOpenView"
import { YouView } from "./YouView"

type ViewId = Extract<SpecimenId, `view-${string}`>

/** What a frame draws for each view entry of the registry (the test keeps the two in step). */
export const VIEW_RENDERERS: Record<ViewId, () => ReactNode> = {
  "view-home": () => <HomeView />,
  "view-you": () => <YouView />,
  "view-path": () => <PathView />,
  "view-people": () => <PeopleView />,
  "view-collections": () => <CollectionsView />,
  "view-guide-open": () => <GuideOpenView />,
  "view-soul-open": () => <SoulOpenView />,
  "view-collection-open": () => <CollectionOpenView />,
  "view-glyphs-open": () => <GlyphsOpenView />,
  "view-achievements-open": () => <AchievementsOpenView />,
  "view-achievement-sheet-open": () => <AchievementSheetOpenView sheet={{ card: "explorer" }} />,
  "view-achievement-drill": () => <AchievementSheetOpenView sheet={{ card: "domains" }} />,
  "view-lab-open": () => <LabOpenView />,
  "view-wallet-open": () => <WalletOpenView />,
  "view-pebble": () => <PebbleDetailView />,
  "view-profile": () => <ProfileView />,
  "view-settings": () => <SettingsView />,
}

function isViewId(id: string): id is ViewId {
  return Object.hasOwn(VIEW_RENDERERS, id)
}

export function renderView(id: string): ReactNode {
  return isViewId(id) ? VIEW_RENDERERS[id]() : null
}
