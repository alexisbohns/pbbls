"use client"

import Link from "next/link"
import type { ReactNode } from "react"
import { ChevronLeft } from "lucide-react"
import { useTranslations } from "next-intl"
import { buttonVariants } from "@/components/ui/button"

type PageHeaderProps = {
  title: string
  backHref?: string
  rightSlot?: ReactNode
}

export function PageHeader({ title, backHref = "/profile", rightSlot }: PageHeaderProps) {
  const t = useTranslations("common")
  return (
    <header className="mb-6 flex items-center gap-3">
      <Link
        href={backHref}
        aria-label={t("back")}
        className={buttonVariants({ variant: "outline", size: "icon" })}
      >
        <ChevronLeft />
      </Link>
      <h1 className="flex-1 font-heading text-2xl font-semibold">{title}</h1>
      {rightSlot}
    </header>
  )
}
