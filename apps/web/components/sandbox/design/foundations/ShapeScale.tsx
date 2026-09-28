import { cn } from "@/lib/utils"

const M3_SHAPES = [
  { label: "none · 0", className: "rounded-m3-none" },
  { label: "extra-small · 4", className: "rounded-m3-extra-small" },
  { label: "small · 8", className: "rounded-m3-small" },
  { label: "medium · 12", className: "rounded-m3-medium" },
  { label: "large · 16", className: "rounded-m3-large" },
  { label: "large-increased · 20", className: "rounded-m3-large-increased" },
  { label: "extra-large · 28", className: "rounded-m3-extra-large" },
  { label: "extra-large-increased · 32", className: "rounded-m3-extra-large-increased" },
  { label: "extra-extra-large · 48", className: "rounded-m3-extra-extra-large" },
  { label: "full", className: "rounded-m3-full" },
]

// Derived from --radius, which .m3 moves to 12px: this row shows what the
// bridge does to every untuned rounded-* in the app.
const APP_RADII = [
  { label: "rounded-sm", className: "rounded-sm" },
  { label: "rounded-md", className: "rounded-md" },
  { label: "rounded-lg", className: "rounded-lg" },
  { label: "rounded-xl", className: "rounded-xl" },
  { label: "rounded-2xl", className: "rounded-2xl" },
  { label: "rounded-3xl", className: "rounded-3xl" },
  { label: "rounded-4xl", className: "rounded-4xl" },
]

function Tiles({ title, shapes }: { title: string; shapes: { label: string; className: string }[] }) {
  return (
    <div className="flex flex-col gap-2">
      <h3 className="font-heading text-xl">{title}</h3>
      <div className="flex flex-wrap gap-4">
        {shapes.map((s) => (
          <figure key={s.label} className="flex w-24 flex-col items-center gap-1">
            <div className={cn("size-20 border border-primary bg-primary/15", s.className)} />
            <figcaption className="text-center font-mono text-[11px] text-muted-foreground">{s.label}</figcaption>
          </figure>
        ))}
      </div>
    </div>
  )
}

export function ShapeScale() {
  return (
    <div className="flex flex-col gap-8">
      <Tiles title="M3 shapes" shapes={M3_SHAPES} />
      <Tiles title="App radii (from --radius)" shapes={APP_RADII} />
    </div>
  )
}
