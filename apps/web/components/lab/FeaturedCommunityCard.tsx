"use client"

import { People } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
import { cn } from "@/lib/utils"
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
          <People className={cn(PBBLS_ICON_SIZE.row, "shrink-0 text-primary")} />
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
