import { Badge } from "@/components/ui/badge"
import type { ContentReport } from "@/lib/moderation/types"

/** The free-text fields worth comparing, per target kind. */
const FIELDS: Record<ContentReport["target_kind"], { key: string; label: string }[]> = {
  pebble: [
    { key: "name", label: "Title" },
    { key: "description", label: "Description" },
  ],
  profile: [
    { key: "handle", label: "Handle" },
    { key: "display_name", label: "Display name" },
  ],
  glyph: [{ key: "name", label: "Name" }],
}

function text(v: unknown): string {
  if (v === null || v === undefined || v === "") return "—"
  return String(v)
}

/**
 * The snapshot beside live content.
 *
 * The whole reason target_snapshot exists: an owner who edits after being
 * reported would otherwise present innocent content to the reviewer. When the
 * two columns disagree, that IS the evidence — so a changed field is called
 * out rather than left for the eye to catch.
 */
export function TargetDiff({ report }: { report: ContentReport }) {
  const fields = FIELDS[report.target_kind] ?? []
  const snap = (report.target_snapshot ?? {}) as Record<string, unknown>
  const live = (report.target_live ?? {}) as Record<string, unknown>

  if (report.target_missing) {
    return (
      <div className="space-y-2 rounded-md border border-dashed p-3">
        <p className="text-xs text-muted-foreground">
          The owner deleted this content themselves. Only the snapshot taken when it was
          reported survives.
        </p>
        <dl className="space-y-1">
          {fields.map(({ key, label }) => (
            <div key={key} className="grid grid-cols-[7rem_1fr] gap-2 text-sm">
              <dt className="text-muted-foreground">{label}</dt>
              <dd className="break-words">{text(snap[key])}</dd>
            </div>
          ))}
        </dl>
      </div>
    )
  }

  return (
    <div className="space-y-1">
      <div className="grid grid-cols-[7rem_1fr_1fr] gap-2 text-xs text-muted-foreground">
        <span />
        <span>When reported</span>
        <span>Now</span>
      </div>
      {fields.map(({ key, label }) => {
        const before = text(snap[key])
        const after = text(live[key])
        const changed = report.target_snapshot !== null && before !== after
        return (
          <div key={key} className="grid grid-cols-[7rem_1fr_1fr] items-start gap-2 text-sm">
            <span className="text-muted-foreground">{label}</span>
            <span className="break-words">{before}</span>
            <span className="break-words">
              {after}
              {changed ? (
                <Badge variant="outline" className="ml-2 align-middle">
                  edited since
                </Badge>
              ) : null}
            </span>
          </div>
        )
      })}
    </div>
  )
}
