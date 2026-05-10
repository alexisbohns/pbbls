"use client"

import Rive, { Layout, Fit, Alignment } from "@rive-app/react-canvas"

type WeekSectionHeaderProps = {
  /** Localized "Week N" / "Semaine N" label. */
  label: string
}

/**
 * Header rendered above each per-week pebble group on the Path screen:
 * a small Rive cairn animation stacked above an uppercased, tracked
 * "WEEK N" / "SEMAINE N" title. Mirrors the iOS `WeekSectionHeader`
 * shipped in PR #378 — same `pbbls-cairn.riv` asset, same visual.
 *
 * The cascade animation that gates the title behind cairn completion on
 * iOS is intentionally omitted here: the issue's acceptance criteria
 * cover grouping + palette + Rive presence, and the cascade adds
 * cross-platform-divergent ceremony for marginal UX value.
 */
export function WeekSectionHeader({ label }: WeekSectionHeaderProps) {
  return (
    <div className="flex flex-col items-center gap-0 py-3">
      <Rive
        src="/animations/pbbls-cairn.riv"
        layout={new Layout({ fit: Fit.Contain, alignment: Alignment.Center })}
        className="size-14"
        aria-hidden="true"
      />
      <span
        className="font-heading text-sm font-semibold uppercase tracking-[0.18em] text-muted-foreground"
        style={{ fontVariantNumeric: "proportional-nums lining-nums" }}
      >
        {label}
      </span>
    </div>
  )
}
