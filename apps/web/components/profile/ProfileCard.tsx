"use client"

import { useTranslations } from "next-intl"
import type { Account, Profile } from "@/lib/types"
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { useFormatDate } from "@/lib/i18n"

interface ProfileCardProps {
  user: Account
  profile: Profile
}

export function ProfileCard({ user, profile }: ProfileCardProps) {
  const t = useTranslations("profile")
  const formatDate = useFormatDate()
  const memberSince = formatDate(user.created_at, { dateStyle: "long" })

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-lg">{profile.display_name}</CardTitle>
      </CardHeader>
      <CardContent>
        <dl className="space-y-3 text-sm">
          <div>
            <dt className="text-muted-foreground">{t("email")}</dt>
            <dd className="font-medium">{user.email}</dd>
          </div>
          <div>
            <dt className="text-muted-foreground">{t("memberSince")}</dt>
            <dd className="font-medium">{memberSince}</dd>
          </div>
        </dl>
      </CardContent>
    </Card>
  )
}
