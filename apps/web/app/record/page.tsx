"use client"

import { useRouter } from "next/navigation"
import Link from "next/link"
import { ArrowLeft } from "lucide-react"
import { useTranslations } from "next-intl"
import { QuickPebbleEditor } from "@/components/path/QuickPebbleEditor"

export default function RecordPage() {
  const router = useRouter()
  const t = useTranslations("record")

  return (
    <section className="mx-auto w-full max-w-lg">
      <header className="mb-4 flex items-center gap-2">
        <Link
          href="/path"
          aria-label={t("back")}
          className="-ml-2 inline-flex size-9 items-center justify-center rounded-lg text-muted-foreground transition-colors hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          <ArrowLeft className="size-5" aria-hidden />
        </Link>
        <h1 className="font-heading text-lg font-semibold">{t("srTitle")}</h1>
      </header>
      <QuickPebbleEditor onPebbleCreated={() => router.push("/path")} />
    </section>
  )
}
