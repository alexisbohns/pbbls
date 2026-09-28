const SAMPLE = "Lunch on the wall by the canal"

const M3_STYLES = [
  { label: "display-large", className: "m3-type-display-large" },
  { label: "display-medium", className: "m3-type-display-medium" },
  { label: "display-small", className: "m3-type-display-small" },
  { label: "headline-large", className: "m3-type-headline-large" },
  { label: "headline-medium", className: "m3-type-headline-medium" },
  { label: "headline-small", className: "m3-type-headline-small" },
  { label: "title-large", className: "m3-type-title-large" },
  { label: "title-medium", className: "m3-type-title-medium" },
  { label: "title-small", className: "m3-type-title-small" },
  { label: "body-large", className: "m3-type-body-large" },
  { label: "body-medium", className: "m3-type-body-medium" },
  { label: "body-small", className: "m3-type-body-small" },
  { label: "label-large", className: "m3-type-label-large" },
  { label: "label-medium", className: "m3-type-label-medium" },
  { label: "label-small", className: "m3-type-label-small" },
]

const APP_FACES = [
  { label: "font-sans (app body)", className: "font-sans text-base" },
  { label: "font-heading (Ysabeau)", className: "font-heading text-2xl" },
  { label: "font-script (Reenie Beanie)", className: "font-script text-3xl" },
  { label: "font-hand (Caveat)", className: "font-hand text-2xl" },
]

function Row({ label, className }: { label: string; className: string }) {
  return (
    <div className="flex flex-col gap-0.5 border-t border-border py-2">
      <span className="font-mono text-[11px] text-muted-foreground">{label}</span>
      <p className={className}>{SAMPLE}</p>
    </div>
  )
}

export function TypeScale() {
  return (
    <div className="grid gap-8 md:grid-cols-2">
      <div className="flex flex-col">
        <h3 className="mb-2 font-heading text-xl">M3 type scale</h3>
        {M3_STYLES.map((s) => (
          <Row key={s.label} {...s} />
        ))}
      </div>
      <div className="flex flex-col">
        <h3 className="mb-2 font-heading text-xl">App faces</h3>
        {APP_FACES.map((s) => (
          <Row key={s.label} {...s} />
        ))}
      </div>
    </div>
  )
}
