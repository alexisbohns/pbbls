import type { MarkStroke } from "@/lib/types"

type StrokeRendererProps = {
  strokes: MarkStroke[]
}

export function StrokeRenderer({ strokes }: StrokeRendererProps) {
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
          className="text-foreground"
        />
      ))}
    </>
  )
}
