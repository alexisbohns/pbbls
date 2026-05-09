import Link from "next/link"
import type { Log } from "@/lib/types"
import { logSummary, logTitle } from "@/lib/utils/log-localized"
import { labAssetUrl } from "@/lib/utils/lab-asset-url"

type AnnouncementRowProps = {
  log: Log
  locale: string
}

// Richer row used for announcements in the Lab feed. Shows an optional
// cover image above the localized title and summary, and links to the
// announcement detail page.
// Mirrors apps/ios/Pebbles/Features/Lab/Components/AnnouncementRow.swift.
export function AnnouncementRow({ log, locale }: AnnouncementRowProps) {
  const coverUrl = labAssetUrl(log.cover_image_path)

  return (
    <Link
      href={`/lab/announcements/${log.id}`}
      className="block overflow-hidden rounded-xl border border-border transition-all duration-100 hover:bg-muted/50 active:scale-[0.99] focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
    >
      {coverUrl && (
        // eslint-disable-next-line @next/next/no-img-element -- public Supabase storage URL, next/image domain config not wired
        <img
          src={coverUrl}
          alt=""
          className="aspect-[16/9] w-full object-cover bg-muted"
          loading="lazy"
        />
      )}
      <div className="px-4 py-3">
        <h3 className="text-sm font-medium">{logTitle(log, locale)}</h3>
        <p className="mt-1 line-clamp-3 text-xs text-muted-foreground">
          {logSummary(log, locale)}
        </p>
      </div>
    </Link>
  )
}
