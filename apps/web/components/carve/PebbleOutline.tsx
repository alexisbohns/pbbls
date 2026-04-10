import type { PebbleShape } from "@/lib/config"

type PebbleOutlineProps = {
  shape: PebbleShape
  clipId: string
}

export function PebbleOutline({ shape, clipId }: PebbleOutlineProps) {
  return (
    <>
      <defs>
        <clipPath id={clipId}>
          <path d={shape.path} />
        </clipPath>
      </defs>
      <path
        d={shape.path}
        fill="none"
        stroke="currentColor"
        strokeWidth={2}
        className="text-border"
      />
    </>
  )
}
