import { NotFoundCard } from "@/components/layout/NotFoundCard"

export function GlyphNotFound() {
  return (
    <NotFoundCard
      title="Glyph not found"
      description="This glyph doesn't exist or may have been removed."
      href="/glyphs"
      linkText="Back to Glyphs"
    />
  )
}
