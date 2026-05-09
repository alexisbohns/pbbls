"use client"

import { useEffect } from "react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { Clock, Sparkles, Route } from "lucide-react"
import { useTranslations } from "next-intl"
import { useAuth } from "@/lib/data/auth-context"
import { Button } from "@/components/ui/button"
import { SEED_PEBBLES } from "@/lib/seed/seed-data"
import { EMOTIONS, type Emotion } from "@/lib/config/emotions"
import { useEmotionLocalized } from "@/lib/i18n"

import type { LucideIcon } from "lucide-react"

const PREVIEW_PEBBLE_IDS = ["pbl-sunrise", "pbl-luna-purr", "pbl-bread"]
const PREVIEW_PEBBLES = SEED_PEBBLES.filter((p) =>
  PREVIEW_PEBBLE_IDS.includes(p.id),
).map((p) => {
  const emotion = EMOTIONS.find(
    (e) => e.name.toLowerCase() === p.emotion_id,
  )
  return { id: p.id, name: p.name, emotion }
})

const FEATURE_KEYS = ["record", "enrich", "grow"] as const
type FeatureKey = (typeof FEATURE_KEYS)[number]
const FEATURE_ICONS: Record<FeatureKey, LucideIcon> = {
  record: Clock,
  enrich: Sparkles,
  grow: Route,
}

function PreviewPebbleRow({ name, emotion }: { name: string; emotion: Emotion | undefined }) {
  const localized = useEmotionLocalized(
    emotion ?? { slug: "", name: "", label: "" },
  )
  return (
    <li className="flex items-center gap-3 rounded-md border bg-card px-4 py-3">
      {emotion && (
        <span
          className="size-3 shrink-0 rounded-full"
          style={{ backgroundColor: emotion.color }}
          aria-hidden="true"
        />
      )}
      <span className="text-sm font-medium">{name}</span>
      {emotion && (
        <span className="ml-auto text-xs text-muted-foreground">
          {localized.name}
        </span>
      )}
    </li>
  )
}

export function LandingPage() {
  const { isAuthenticated, isLoading } = useAuth()
  const router = useRouter()
  const t = useTranslations("landing")
  const tBrand = useTranslations("brand")

  useEffect(() => {
    if (!isLoading && isAuthenticated) {
      router.replace("/path")
    }
  }, [isLoading, isAuthenticated, router])

  if (isAuthenticated) return null

  return (
    <section className="flex min-h-screen flex-col items-center justify-center px-6 text-center">
      <h1 className="text-2xl font-heading font-bold tracking-tight">
        {tBrand("name")}
      </h1>
      <p className="mt-3 max-w-sm text-lg text-muted-foreground">
        {t("tagline")}
      </p>

      <ul className="mt-10 flex max-w-sm flex-col gap-3 text-left border rounded-lg p-6 bg-card">
        {FEATURE_KEYS.map((key) => {
          const Icon = FEATURE_ICONS[key]
          return (
            <li key={key} className="flex items-start gap-3">
              <Icon
                aria-hidden="true"
                className="mt-0.5 size-5 shrink-0 text-primary"
              />
              <div>
                <p className="text-sm font-semibold">{t(`features.${key}.title`)}</p>
                <p className="text-sm text-muted-foreground">
                  {t(`features.${key}.description`)}
                </p>
              </div>
            </li>
          )
        })}
      </ul>

      <ul className="mt-8 flex max-w-sm flex-col gap-2 w-full" aria-label={t("exampleAria")}>
        {PREVIEW_PEBBLES.map((p) => (
          <PreviewPebbleRow key={p.id} name={p.name} emotion={p.emotion} />
        ))}
      </ul>

      <div className="mt-10 flex flex-col items-center gap-3">
        <Button size="lg" render={<Link href="/register" />}>
          {t("getStarted")}
        </Button>
        <p className="text-sm text-muted-foreground">
          {t("haveAccount")}{" "}
          <Link
            href="/login"
            className="text-foreground underline underline-offset-4 hover:text-primary"
          >
            {t("logIn")}
          </Link>
        </p>
      </div>
    </section>
  )
}
