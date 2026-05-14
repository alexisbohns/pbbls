"use client"

import { useRouter } from "next/navigation"
import type { ReactNode } from "react"
import { ChevronLeft } from "lucide-react"
import { useTranslations } from "next-intl"
import { Button } from "@/components/ui/button"

type PageHeaderProps = {
  title: string
  fallbackHref?: string
  rightSlot?: ReactNode
}

export function PageHeader({ title, fallbackHref = "/path", rightSlot }: PageHeaderProps) {
  const router = useRouter()
  const t = useTranslations("common")

  const handleBack = () => {
    // window.history.length > 1 means there's a previous entry in the tab's
    // session history; if the page was opened directly via URL it's 1.
    if (typeof window !== "undefined" && window.history.length > 1) {
      router.back()
    } else {
      router.push(fallbackHref)
    }
  }

  return (
    <header className="mb-6 flex items-center gap-3">
      <Button
        variant="outline"
        size="icon"
        aria-label={t("back")}
        onClick={handleBack}
      >
        <ChevronLeft />
      </Button>
      <h1 className="flex-1 font-heading text-2xl font-semibold">{title}</h1>
      {rightSlot}
    </header>
  )
}
