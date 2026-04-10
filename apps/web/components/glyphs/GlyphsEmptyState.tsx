import Link from "next/link"
import { EmptyState } from "@/components/layout/EmptyState"

export function GlyphsEmptyState() {
  return (
    <EmptyState
      title="No glyphs yet"
      description="Glyphs are symbols you carve on pebble surfaces. Create your first one."
      action={
        <Link
          href="/carve"
          className="text-sm font-medium text-primary underline underline-offset-4 hover:text-primary/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
        >
          Carve a glyph
        </Link>
      }
    />
  )
}
