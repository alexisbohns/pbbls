import { DesignSection } from "../DesignSection"
import { Specimen } from "../Specimen"
import { DeviceFrame } from "./DeviceFrame"
import { CollectionOpenView } from "./CollectionOpenView"
import { GuideOpenView } from "./GuideOpenView"
import { SoulOpenView } from "./SoulOpenView"
import { WalletOpenView } from "./WalletOpenView"
import { HomeView } from "./HomeView"
import { CollectionsView } from "./CollectionsView"
import { PathView } from "./PathView"
import { PeopleView } from "./PeopleView"
import { PebbleDetailView } from "./PebbleDetailView"
import { ProfileView } from "./ProfileView"
import { SettingsView } from "./SettingsView"
import { YouView } from "./YouView"

export function ViewsSection() {
  return (
    <DesignSection
      id="views"
      title="Views"
      description="Whole screens composed the way their app/ pages compose them, fed by the fixture store. Writes do nothing, links are inert."
    >
      <Specimen
        id="view-home"
        note="Fixtures stand in for the month, the wraps and the guides (the mock's October 2026). Camera and Quick moment cannot save here: there is no session."
      >
        <DeviceFrame>
          <HomeView />
        </DeviceFrame>
      </Specimen>
      <Specimen
        id="view-you"
        note="Fixtures stand in for the served counts, the achievements shelf and the Lab. Settings and every row link to the real routes."
      >
        <DeviceFrame>
          <YouView />
        </DeviceFrame>
      </Specimen>
      <Specimen
        id="view-path"
        note="Tapping a pebble opens the peek; its emotion and domain tiles share the Pebble detail limitation below, and its Edit button navigates to the real (empty) edit route rather than through a link."
      >
        <DeviceFrame>
          <PathView />
        </DeviceFrame>
      </Specimen>
      <Specimen id="view-people" note="The tab root on the M3 ground, with the app navigation. Fed by the fixture store.">
        <DeviceFrame>
          <PeopleView />
        </DeviceFrame>
      </Specimen>
      <Specimen id="view-collections" note="The tab root on the M3 ground, with the app navigation. Fed by the fixture store.">
        <DeviceFrame>
          <CollectionsView />
        </DeviceFrame>
      </Specimen>
      <Specimen
        id="view-guide-open"
        note="A detail beside its tab: from 1024 px it covers the right area (choose Fill); below, it is the overlay. Close does nothing here."
      >
        <DeviceFrame>
          <GuideOpenView />
        </DeviceFrame>
      </Specimen>
      <Specimen id="view-soul-open" note="A soul beside People, from the fixture store. Writes do nothing.">
        <DeviceFrame>
          <SoulOpenView />
        </DeviceFrame>
      </Specimen>
      <Specimen id="view-collection-open" note="A collection beside Collections, from the fixture store. Writes do nothing.">
        <DeviceFrame>
          <CollectionOpenView />
        </DeviceFrame>
      </Specimen>
      <Specimen id="view-wallet-open" note="The wallet over the Path's line. It reads the network, so it shows its empty state here.">
        <DeviceFrame>
          <WalletOpenView />
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
        note="The profile before You (#1384): its components stay for the public profile and this sandbox. The achievements shelf reads through provider methods, not the store, so it shows its empty state."
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
