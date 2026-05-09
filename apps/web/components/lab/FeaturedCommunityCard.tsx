"use client"

import { MessagesSquare } from "lucide-react"
import { useTranslations } from "next-intl"
import { Card, CardContent } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { LAB_CONFIG } from "@/lib/config/lab"

// Pinned card at the top of the Lab feed inviting users to join the
// Pebbles WhatsApp community. Static content — link lives in `LabConfig`.
// Mirrors apps/ios/Pebbles/Features/Lab/Components/FeaturedCommunityCard.swift.
export function FeaturedCommunityCard() {
  const t = useTranslations("lab.community")
  return (
    <Card>
      <CardContent className="flex flex-col gap-3">
        <div className="flex items-start gap-3">
          <MessagesSquare className="size-5 shrink-0 text-primary" aria-hidden />
          <div className="flex flex-col gap-0.5">
            <h2 className="text-sm font-medium">{t("title")}</h2>
            <p className="text-xs text-muted-foreground">
              {t("description")}
            </p>
          </div>
        </div>
        <Button
          size="sm"
          className="w-full"
          render={
            <a
              href={LAB_CONFIG.whatsappInviteURL}
              target="_blank"
              rel="noreferrer noopener"
            />
          }
        >
          {t("cta")}
        </Button>
      </CardContent>
    </Card>
  )
}
