import { ActiveUsersChart } from "@/components/analytics/ActiveUsersChart"
import { DomainShare } from "@/components/analytics/DomainShare"
import { EmotionShare } from "@/components/analytics/EmotionShare"
import { KpiCard } from "@/components/analytics/KpiCard"
import { PebbleEnrichment } from "@/components/analytics/PebbleEnrichment"
import { PebbleVolumeChart } from "@/components/analytics/PebbleVolumeChart"
import { RetentionHeatmap } from "@/components/analytics/RetentionHeatmap"
import { Sparkline } from "@/components/analytics/Sparkline"
import { UserAverages } from "@/components/analytics/UserAverages"
import {
  denseFixture,
  emptyFixture,
  sparseFixture,
} from "@/components/analytics/__fixtures__/activeUsers"
import { kpiFixture } from "@/components/analytics/__fixtures__/kpi"
import {
  denseEnrichmentFixture,
  emptyEnrichmentFixture,
  sparseEnrichmentFixture,
} from "@/components/analytics/__fixtures__/pebbleEnrichment"
import {
  denseVolumeFixture,
  emptyVolumeFixture,
  sparseVolumeFixture,
} from "@/components/analytics/__fixtures__/pebbleVolume"
import {
  denseRetentionFixture,
  emptyRetentionFixture,
} from "@/components/analytics/__fixtures__/retentionCohorts"
import {
  denseUserAveragesFixture,
  emptyUserAveragesFixture,
  sparseUserAveragesFixture,
} from "@/components/analytics/__fixtures__/userAverages"
import {
  denseEmotionShareCatalog,
  denseEmotionShareSnapshot,
  denseEmotionShareTotalPebbles,
  denseEmotionShareWeekly,
  emptyEmotionShareCatalog,
  emptyEmotionShareSnapshot,
  emptyEmotionShareTotalPebbles,
  emptyEmotionShareWeekly,
  sparseEmotionShareCatalog,
  sparseEmotionShareSnapshot,
  sparseEmotionShareTotalPebbles,
  sparseEmotionShareWeekly,
} from "@/components/analytics/__fixtures__/emotionShare"
import {
  denseDomainShareBottomMover,
  denseDomainShareSnapshot,
  denseDomainShareTopMover,
  denseDomainShareTotalPebbles,
  emptyDomainShareSnapshot,
  emptyDomainShareTotalPebbles,
  sparseDomainShareSnapshot,
  sparseDomainShareTotalPebbles,
} from "@/components/analytics/__fixtures__/domainShare"

export default function AnalyticsPlaygroundPage() {
  const sparkValues = kpiFixture.map((r) => r.dau ?? 0)

  return (
    <div className="space-y-10">
      <header>
        <h1 className="text-2xl font-semibold">Playground · Analytics</h1>
        <p className="text-sm text-muted-foreground">
          Renders analytics components from fixtures. No live data calls.
        </p>
      </header>

      <section className="space-y-4">
        <h2 className="text-sm font-medium uppercase text-muted-foreground">KpiCard variants</h2>
        <div className="grid grid-cols-2 gap-4 md:grid-cols-3">
          <KpiCard
            label="DAU"
            value={142}
            subLabel="trailing 7-day avg"
            sparkline={sparkValues}
            delta={{ absolute: 8, direction: "up" }}
          />
          <KpiCard
            label="DAU"
            value={142}
            subLabel="trailing 7-day avg"
            sparkline={sparkValues}
            delta={{ absolute: -3, direction: "down" }}
          />
          <KpiCard
            label="MAU"
            value={1212}
            subLabel="rolling 30 days"
            sparkline={sparkValues}
            delta={{ absolute: 0, direction: "flat" }}
          />
          <KpiCard label="Total users" value={1287} subLabel="all signups" />
          <KpiCard label="DAU / MAU" value="—" subLabel="No data yet" />
          <KpiCard
            label="DAU / MAU"
            value={11.7}
            unit="%"
            subLabel="stickiness"
            delta={{ absolute: 0.3, direction: "up", unit: "pp" }}
          />
        </div>
      </section>

      <section className="space-y-4">
        <h2 className="text-sm font-medium uppercase text-muted-foreground">Sparkline</h2>
        <div className="text-foreground/60">
          <Sparkline values={sparkValues} width={200} height={40} ariaLabel="DAU sparkline" />
        </div>
      </section>

      <section className="space-y-4">
        <h2 className="text-sm font-medium uppercase text-muted-foreground">
          ActiveUsersChart — dense (90 days)
        </h2>
        <ActiveUsersChart data={denseFixture} />
      </section>

      <section className="space-y-4">
        <h2 className="text-sm font-medium uppercase text-muted-foreground">
          ActiveUsersChart — sparse (12 days)
        </h2>
        <ActiveUsersChart data={sparseFixture} />
      </section>

      <section className="space-y-4">
        <h2 className="text-sm font-medium uppercase text-muted-foreground">
          ActiveUsersChart — empty
        </h2>
        <ActiveUsersChart data={emptyFixture} />
      </section>

      <section className="space-y-4">
        <h2 className="text-sm font-medium uppercase text-muted-foreground">
          RetentionHeatmap — 8 cohorts, varying retention
        </h2>
        <div className="max-w-md">
          <RetentionHeatmap rows={denseRetentionFixture} />
        </div>
      </section>

      <section className="space-y-4">
        <h2 className="text-sm font-medium uppercase text-muted-foreground">
          RetentionHeatmap — empty
        </h2>
        <div className="max-w-md">
          <RetentionHeatmap rows={emptyRetentionFixture} />
        </div>
      </section>

      <section className="space-y-4">
        <h2 className="text-sm font-medium uppercase text-muted-foreground">
          PebbleVolumeChart — dense (90 days)
        </h2>
        <PebbleVolumeChart data={denseVolumeFixture} />
      </section>

      <section className="space-y-4">
        <h2 className="text-sm font-medium uppercase text-muted-foreground">
          PebbleVolumeChart — sparse (12 days)
        </h2>
        <PebbleVolumeChart data={sparseVolumeFixture} />
      </section>

      <section className="space-y-4">
        <h2 className="text-sm font-medium uppercase text-muted-foreground">
          PebbleVolumeChart — empty
        </h2>
        <PebbleVolumeChart data={emptyVolumeFixture} />
      </section>

      <section className="space-y-4">
        <h2 className="text-sm font-medium uppercase text-muted-foreground">
          PebbleEnrichment — dense
        </h2>
        <div className="max-w-sm">
          <PebbleEnrichment row={denseEnrichmentFixture} />
        </div>
      </section>

      <section className="space-y-4">
        <h2 className="text-sm font-medium uppercase text-muted-foreground">
          PebbleEnrichment — sparse
        </h2>
        <div className="max-w-sm">
          <PebbleEnrichment row={sparseEnrichmentFixture} />
        </div>
      </section>

      <section className="space-y-4">
        <h2 className="text-sm font-medium uppercase text-muted-foreground">
          PebbleEnrichment — empty
        </h2>
        <div className="max-w-sm">
          <PebbleEnrichment row={emptyEnrichmentFixture} />
        </div>
      </section>

      <section className="space-y-4">
        <h2 className="text-sm font-medium uppercase text-muted-foreground">
          UserAverages — dense (12 weeks)
        </h2>
        <UserAverages data={denseUserAveragesFixture} />
      </section>

      <section className="space-y-4">
        <h2 className="text-sm font-medium uppercase text-muted-foreground">
          UserAverages — sparse (4 weeks)
        </h2>
        <UserAverages data={sparseUserAveragesFixture} />
      </section>

      <section className="space-y-4">
        <h2 className="text-sm font-medium uppercase text-muted-foreground">
          UserAverages — empty
        </h2>
        <UserAverages data={emptyUserAveragesFixture} />
      </section>

      <section className="space-y-4">
        <h2 className="text-sm font-medium uppercase text-muted-foreground">
          EmotionShare — dense
        </h2>
        <div className="max-w-xl">
          <EmotionShare
            snapshot={denseEmotionShareSnapshot}
            weekly={denseEmotionShareWeekly}
            catalog={denseEmotionShareCatalog}
            totalPebbles={denseEmotionShareTotalPebbles}
            rangeLabel="30 days"
          />
        </div>
      </section>

      <section className="space-y-4">
        <h2 className="text-sm font-medium uppercase text-muted-foreground">
          EmotionShare — sparse
        </h2>
        <div className="max-w-xl">
          <EmotionShare
            snapshot={sparseEmotionShareSnapshot}
            weekly={sparseEmotionShareWeekly}
            catalog={sparseEmotionShareCatalog}
            totalPebbles={sparseEmotionShareTotalPebbles}
            rangeLabel="30 days"
          />
        </div>
      </section>

      <section className="space-y-4">
        <h2 className="text-sm font-medium uppercase text-muted-foreground">
          EmotionShare — empty
        </h2>
        <div className="max-w-xl">
          <EmotionShare
            snapshot={emptyEmotionShareSnapshot}
            weekly={emptyEmotionShareWeekly}
            catalog={emptyEmotionShareCatalog}
            totalPebbles={emptyEmotionShareTotalPebbles}
            rangeLabel="30 days"
          />
        </div>
      </section>

      <section className="space-y-4">
        <h2 className="text-sm font-medium uppercase text-muted-foreground">
          DomainShare — dense (with movers)
        </h2>
        <div className="max-w-xl">
          <DomainShare
            rows={denseDomainShareSnapshot}
            totalPebbles={denseDomainShareTotalPebbles}
            rangeLabel="30 days"
            topMover={denseDomainShareTopMover}
            bottomMover={denseDomainShareBottomMover}
          />
        </div>
      </section>

      <section className="space-y-4">
        <h2 className="text-sm font-medium uppercase text-muted-foreground">
          DomainShare — sparse (no prior period)
        </h2>
        <div className="max-w-xl">
          <DomainShare
            rows={sparseDomainShareSnapshot}
            totalPebbles={sparseDomainShareTotalPebbles}
            rangeLabel="all time"
            topMover={null}
            bottomMover={null}
          />
        </div>
      </section>

      <section className="space-y-4">
        <h2 className="text-sm font-medium uppercase text-muted-foreground">
          DomainShare — empty
        </h2>
        <div className="max-w-xl">
          <DomainShare
            rows={emptyDomainShareSnapshot}
            totalPebbles={emptyDomainShareTotalPebbles}
            rangeLabel="30 days"
            topMover={null}
            bottomMover={null}
          />
        </div>
      </section>
    </div>
  )
}
