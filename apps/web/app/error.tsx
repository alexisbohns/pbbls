"use client"

import { useEffect } from "react"
import { useTranslations } from "next-intl"
import { Button } from "@/components/ui/button"

export default function Error({
  error,
  unstable_retry,
}: {
  error: Error & { digest?: string }
  unstable_retry: () => void
}) {
  const t = useTranslations("errors")

  useEffect(() => {
    console.error(error)
  }, [error])

  return (
    <section className="flex flex-col items-center justify-center gap-4 py-20 text-center">
      <h1 className="text-2xl font-semibold">{t("title")}</h1>
      <p className="text-sm text-muted-foreground">{t("description")}</p>
      <Button variant="outline" onClick={() => unstable_retry()}>
        {t("retry")}
      </Button>
    </section>
  )
}
