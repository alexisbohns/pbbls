import { UploadAdjust } from "./_components/UploadAdjust"

export default function NewGlyphPage() {
  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <h1 className="text-lg font-semibold">Upload a glyph</h1>
      <UploadAdjust />
    </div>
  )
}
