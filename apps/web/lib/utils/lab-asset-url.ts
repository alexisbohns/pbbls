import { LAB_CONFIG } from "@/lib/config/lab"

// Builds the public URL for a lab asset (e.g. announcement cover image).
// The `lab-assets` bucket is public, so we hit `/storage/v1/object/public/...`
// directly without needing a signed URL. Mirrors `LabView.coverURL(for:)` on iOS.
export function labAssetUrl(path: string | null): string | null {
  if (!path) return null
  const base = process.env.NEXT_PUBLIC_SUPABASE_URL
  if (!base) return null
  const trimmedBase = base.replace(/\/$/, "")
  const trimmedPath = path.replace(/^\//, "")
  return `${trimmedBase}/storage/v1/object/public/${LAB_CONFIG.assetsBucket}/${trimmedPath}`
}
