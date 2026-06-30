import Link from "next/link"
import { Button } from "@/components/ui/button"
import { listSubmissions } from "@/lib/pebblestore/fetchers"
import type { SubmissionStatus } from "@/lib/pebblestore/types"
import { ModerationQueue } from "./_components/ModerationQueue"

const STATUSES: SubmissionStatus[] = ["pending", "approved", "rejected"]

function isStatus(v: string | undefined): v is SubmissionStatus {
  return v === "pending" || v === "approved" || v === "rejected"
}

export default async function GlyphModerationPage({
  searchParams,
}: {
  searchParams: Promise<{ status?: string }>
}) {
  const { status } = await searchParams
  const active: SubmissionStatus = isStatus(status) ? status : "pending"

  const submissions = await listSubmissions(active)

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-lg font-semibold">Glyph moderation</h1>
        <Button render={<Link href="/pebblestore/glyphs/new" />}>Upload glyph</Button>
      </div>

      <nav className="flex gap-2">
        {STATUSES.map((s) => (
          <Button
            key={s}
            size="sm"
            variant={s === active ? "default" : "outline"}
            render={<Link href={`/pebblestore/glyphs?status=${s}`} />}
          >
            {s[0].toUpperCase() + s.slice(1)}
          </Button>
        ))}
      </nav>

      <ModerationQueue submissions={submissions} />
    </div>
  )
}
