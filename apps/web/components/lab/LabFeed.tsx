"use client"

import Link from "next/link"
import { ArrowRight } from "lucide-react"
import { useLabFeed } from "@/lib/data/useLab"
import { FeaturedCommunityCard } from "@/components/lab/FeaturedCommunityCard"
import { AnnouncementRow } from "@/components/lab/AnnouncementRow"
import { LogRow } from "@/components/lab/LogRow"
import { ReactionButton } from "@/components/lab/ReactionButton"

const LOCALE = "en"

function SectionTitle({ children }: { children: React.ReactNode }) {
  return (
    <h2 className="mb-3 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
      {children}
    </h2>
  )
}

function SeeAllLink({ href, label }: { href: string; label: string }) {
  return (
    <Link
      href={href}
      className="mt-3 inline-flex items-center gap-1.5 text-xs font-medium text-primary hover:underline"
    >
      {label}
      <ArrowRight className="size-3.5" aria-hidden />
    </Link>
  )
}

export function LabFeed() {
  const {
    announcements,
    changelog,
    initiatives,
    backlog,
    reactedIds,
    loading,
    error,
    toggleReaction,
  } = useLabFeed()

  if (loading) {
    return <p className="text-sm text-muted-foreground">Loading…</p>
  }

  if (error) {
    return (
      <p className="text-sm text-muted-foreground">
        Couldn’t load the Lab. Please try again.
      </p>
    )
  }

  return (
    <div className="flex flex-col gap-8">
      <FeaturedCommunityCard />

      {announcements.length > 0 && (
        <section>
          <SectionTitle>Announcements</SectionTitle>
          <ul className="flex flex-col gap-2">
            {announcements.map((log) => (
              <li key={log.id}>
                <AnnouncementRow log={log} locale={LOCALE} />
              </li>
            ))}
          </ul>
        </section>
      )}

      {changelog.length > 0 && (
        <section>
          <SectionTitle>Changelog</SectionTitle>
          <ul className="flex flex-col gap-2">
            {changelog.map((log) => (
              <li key={log.id}>
                <LogRow log={log} locale={LOCALE} />
              </li>
            ))}
          </ul>
          <SeeAllLink href="/lab/changelog" label="See all" />
        </section>
      )}

      {initiatives.length > 0 && (
        <section>
          <SectionTitle>In progress</SectionTitle>
          <ul className="flex flex-col gap-2">
            {initiatives.map((log) => (
              <li key={log.id}>
                <LogRow log={log} locale={LOCALE} />
              </li>
            ))}
          </ul>
        </section>
      )}

      {backlog.length > 0 && (
        <section>
          <SectionTitle>Backlog</SectionTitle>
          <ul className="flex flex-col gap-2">
            {backlog.map((log) => (
              <li key={log.id}>
                <LogRow
                  log={log}
                  locale={LOCALE}
                  trailing={
                    <ReactionButton
                      count={log.reaction_count}
                      isReacted={reactedIds.has(log.id)}
                      onToggle={() => void toggleReaction(log.id)}
                    />
                  }
                />
              </li>
            ))}
          </ul>
          <SeeAllLink href="/lab/backlog" label="See all" />
        </section>
      )}
    </div>
  )
}
