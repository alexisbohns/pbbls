import { DesignSection } from "../DesignSection"
import { Specimen } from "../Specimen"
import { DeviceFrame } from "./DeviceFrame"
import { PathView } from "./PathView"
import { PebbleDetailView } from "./PebbleDetailView"
import { ProfileView } from "./ProfileView"
import { SettingsView } from "./SettingsView"

export function ViewsSection() {
  return (
    <DesignSection
      id="views"
      title="Views"
      description="Whole screens composed the way their app/ pages compose them, fed by the fixture store. Writes do nothing, links are inert."
    >
      <Specimen
        id="view-path"
        note="Tapping a pebble opens the peek; its emotion and domain tiles share the Pebble detail limitation below."
      >
        <DeviceFrame>
          <PathView />
        </DeviceFrame>
      </Specimen>
      <Specimen
        id="view-pebble"
        note="The emotion and domain tiles read reference views from Supabase that cannot be seeded, so here they show their generic fallback."
      >
        <DeviceFrame>
          <PebbleDetailView />
        </DeviceFrame>
      </Specimen>
      <Specimen
        id="view-profile"
        note="The achievements shelf reads through provider methods, not the store, so it shows its empty state."
      >
        <DeviceFrame>
          <ProfileView />
        </DeviceFrame>
      </Specimen>
      <Specimen
        id="view-settings"
        note="Appearance is left out: its controls would change your real theme, locale and color world."
      >
        <DeviceFrame>
          <SettingsView />
        </DeviceFrame>
      </Specimen>
    </DesignSection>
  )
}
