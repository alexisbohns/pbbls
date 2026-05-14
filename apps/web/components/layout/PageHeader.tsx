"use client"

import Link from "next/link"
import type { ReactNode } from "react"
import { ChevronLeft } from "lucide-react"
import { useTranslations } from "next-intl"
import { Button } from "@/components/ui/button"

type PageHeaderProps = {
  title: string
  backHref?: string
  rightSlot?: ReactNode
}

export function PageHeader({ title, backHref = "/path", rightSlot }: PageHeaderProps) {
  const t = useTranslations("common")
  return (
    <header className="mb-6 flex items-center gap-3">
      <Button
        variant="outline"
        size="icon"
        aria-label={t("back")}
        render={<Link href={backHref} />}
      >
        <ChevronLeft />
      </Button>
      <h1 className="flex-1 font-heading text-2xl font-semibold">{title}</h1>
      {rightSlot}
    </header>
  )
}
