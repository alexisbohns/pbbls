"use client"

import { useTranslations } from "next-intl"
import { useLocale } from "@/lib/i18n"
import { useLogList, type LogListMode } from "@/lib/data/useLab"
import { LogTimeline } from "@/components/lab/LogTimeline"
import { ReactionButton } from "@/components/lab/ReactionButton"

type LabLogListProps = {
  mode: LogListMode
}

// Full "See all" list for the Lab tab's changelog or backlog section.
// Re-uses the same data fetcher as the parent feed but without the top-N
// limit. Backlog mode attaches a reaction toggle on each row.
// Mirrors apps/ios/Pebbles/Features/Lab/Views/LogListView.swift.
export function LabLogList({ mode }: LabLogListProps) {
  const { logs, reactedIds, loading, error, toggleReaction } = useLogList(mode)
  const t = useTranslations("lab")
  const { locale } = useLocale()

  if (loading) {
    return <p className="text-sm text-muted-foreground">{t("loading")}</p>
  }

  if (error) {
    return <p className="text-sm text-muted-foreground">{t("listError")}</p>
  }

  if (logs.length === 0) {
    return <p className="text-sm text-muted-foreground">{t("empty")}</p>
  }

  return (
    <LogTimeline
      mode={mode}
      logs={logs}
      locale={locale}
      renderTrailing={
        mode === "backlog"
          ? (log) => (
              <ReactionButton
                count={log.reaction_count}
                isReacted={reactedIds.has(log.id)}
                onToggle={() => void toggleReaction(log.id)}
              />
            )
          : undefined
      }
    />
  )
}
