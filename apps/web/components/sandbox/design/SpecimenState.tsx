import type { ReactNode } from "react"

/** A captioned state inside a Specimen ("disabled", "invalid", a variant name…). */
export function SpecimenState({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-1.5">
      <span className="font-mono text-[11px] text-muted-foreground">{label}</span>
      {children}
    </div>
  )
}
