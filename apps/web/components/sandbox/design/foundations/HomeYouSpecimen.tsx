import { M3Island } from "@/components/layout/M3Island"
import { Card } from "@/components/ui/card"

const ROLES: readonly (readonly [role: string, sample: string])[] = [
  ["pb-type-greeting", "Good evening, Alexis"],
  ["pb-type-lead", "The day folds quietly."],
  ["pb-type-date", "Saturday 31 October"],
  ["pb-type-section", "Monthly wraps"],
  ["pb-type-figure", "2 of 5"],
  ["pb-type-figure-small", "of"],
  ["pb-type-counter", "1,240"],
  ["pb-type-day", "26"],
  ["pb-type-annotation", "oct"],
  ["pb-type-card-title", "Cut from one stone"],
  ["pb-type-card-month", "October"],
  ["pb-type-kicker", "Lab notes · 31 October"],
  ["pb-type-since", "Member since March 2026"],
  ["pb-type-hand", "Alexis"],
  ["pb-type-row-title", "Record your first moment"],
  ["pb-type-row-line", "A moment is anything worth keeping."],
  ["pb-type-label", "Quick moment"],
  ["pb-type-caption", "First pebble"],
]

/**
 * The Home and You foundations (#1384): the pb-type-* roles, the card and the
 * first-five box, inside an M3 island so they stay M3 while the page around
 * them wears a colour world (switch the sandbox to Current to check it).
 */
export function HomeYouSpecimen() {
  return (
    <div id="home-you-specimen" className="flex flex-col gap-3">
      <h3 className="font-heading text-xl">Home and You</h3>
      <p className="max-w-2xl text-sm text-muted-foreground">
        The type roles and surfaces of #1384, in an <code>.m3</code> island: it keeps the M3 roles whatever theme the
        page is on.
      </p>
      <M3Island className="flex flex-col gap-6 rounded-xl p-6">
        <ul className="flex flex-col gap-3">
          {ROLES.map(([role, sample]) => (
            <li key={role} className="flex flex-col gap-1">
              <code className="text-xs text-m3-on-surface-variant">{role}</code>
              <span className={role}>{sample}</span>
            </li>
          ))}
        </ul>
        <div className="flex flex-wrap items-start gap-4">
          <Card className="pb-card w-[152px] p-[18px_16px_18px_18px]">
            <span className="pb-type-card-month">October</span>
            <span className="pb-type-kicker text-m3-on-surface-variant">2026</span>
          </Card>
          <div className="max-w-xs rounded-[26px] bg-m3-surface-container p-6">
            <span className="pb-type-row-line">The first-five box on surface-container.</span>
          </div>
        </div>
      </M3Island>
    </div>
  )
}
