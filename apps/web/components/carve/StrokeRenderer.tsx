import type { MarkStroke } from "@/lib/types"

type StrokeRendererProps = {
  strokes: MarkStroke[]
  className?: string
}

export function StrokeRenderer({ strokes, className }: StrokeRendererProps) {
  return (
    <>
      {strokes.map((stroke, i) => (
        <path
          key={i}
          d={stroke.d}
          fill="none"
          stroke="currentColor"
          strokeWidth={stroke.width}
          strokeLinecap="round"
          strokeLinejoin="round"
          className={className ?? "text-foreground"}
        />
      ))}
    </>
  )
}
