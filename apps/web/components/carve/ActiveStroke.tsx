import { pointsToSvgPath, type Point } from "@/lib/utils/simplify-path"

type ActiveStrokeProps = {
  points: Point[]
  width: number
}

export function ActiveStroke({ points, width }: ActiveStrokeProps) {
  if (points.length === 0) return null

  const d = pointsToSvgPath(points)

  return (
    <path
      d={d}
      fill="none"
      stroke="currentColor"
      strokeWidth={width}
      strokeLinecap="round"
      strokeLinejoin="round"
      className="text-foreground opacity-70"
    />
  )
}
