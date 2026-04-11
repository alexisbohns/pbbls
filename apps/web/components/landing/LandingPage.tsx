"use client"

import { useEffect } from "react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { Clock, Sparkles, Route } from "lucide-react"
import { useAuth } from "@/lib/data/auth-context"
import { Button } from "@/components/ui/button"
import { SEED_PEBBLES } from "@/lib/seed/seed-data"
import { EMOTIONS } from "@/lib/config/emotions"

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

const FEATURES: ReadonlyArray<{
  icon: LucideIcon
  title: string
  description: string
}> = [
  {
    icon: Clock,
    title: "Record in seconds",
    description:
      "Capture moments as they happen \u2014 no blank page, no pressure.",
  },
  {
    icon: Sparkles,
    title: "Enrich with meaning",
    description: "Add emotions, people, and reflections to each pebble.",
  },
  {
    icon: Route,
    title: "Grow your path",
    description: "Look back at your journey, at your own pace.",
  },
]

export function LandingPage() {
  const { isAuthenticated, isLoading } = useAuth()
  const router = useRouter()

  useEffect(() => {
    if (!isLoading && isAuthenticated) {
      router.replace("/path")
    }
  }, [isLoading, isAuthenticated, router])

  // Don't hide during loading — the landing page should render immediately
  // for unauthenticated visitors. Authenticated users get redirected via
  // the useEffect above; a brief flash is acceptable.
  if (isAuthenticated) return null

  return (
    <section className="flex min-h-screen flex-col items-center justify-center px-6 text-center">
      <h1 className="text-2xl font-heading font-bold tracking-tight">
        Pebbles
      </h1>
      <p className="mt-3 max-w-sm text-lg text-muted-foreground">
        Collect meaningful moments.
      </p>

      <ul className="mt-10 flex max-w-sm flex-col gap-3 text-left border rounded-lg p-6 bg-card">
        {FEATURES.map((feature) => (
          <li key={feature.title} className="flex items-start gap-3">
            <feature.icon
              aria-hidden="true"
              className="mt-0.5 size-5 shrink-0 text-primary"
            />
            <div>
              <p className="text-sm font-semibold">{feature.title}</p>
              <p className="text-sm text-muted-foreground">
                {feature.description}
              </p>
            </div>
          </li>
        ))}
      </ul>

      <ul className="mt-8 flex max-w-sm flex-col gap-2 w-full" aria-label="Example pebbles">
        {PREVIEW_PEBBLES.map((p) => (
          <li
            key={p.id}
            className="flex items-center gap-3 rounded-md border bg-card px-4 py-3"
          >
            {p.emotion && (
              <span
                className="size-3 shrink-0 rounded-full"
                style={{ backgroundColor: p.emotion.color }}
                aria-hidden="true"
              />
            )}
            <span className="text-sm font-medium">{p.name}</span>
            {p.emotion && (
              <span className="ml-auto text-xs text-muted-foreground">
                {p.emotion.name}
              </span>
            )}
          </li>
        ))}
      </ul>

      <div className="mt-10 flex flex-col items-center gap-3">
        <Button size="lg" render={<Link href="/register" />}>
          Get started
        </Button>
        <p className="text-sm text-muted-foreground">
          Already have an account?{" "}
          <Link
            href="/login"
            className="text-foreground underline underline-offset-4 hover:text-primary"
          >
            Log in
          </Link>
        </p>
      </div>
    </section>
  )
}
