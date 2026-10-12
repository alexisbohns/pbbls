"use client"

import { Fragment } from "react"
import { useTranslations } from "next-intl"
import { useLocale } from "@/lib/i18n"
import type { Mark, Soul } from "@/lib/types"
import { peopleLine } from "@/lib/utils/spine/people-line"
import { cn } from "@/lib/utils"
import { SoulMark } from "./SoulMark"

type PeopleLineProps = {
  soulIds: string[]
  /** The persons-of-the-week order: the most present are named first. */
  order: readonly string[]
  soulMap: ReadonlyMap<string, Soul>
  markMap: ReadonlyMap<string, Mark>
  /** `entry`: a sentence under the name. `card`: a centred strip, "with" unspoken. */
  variant: "entry" | "card"
  className?: string
}

/**
 * Who was there (#1052): "with [g] MARA & [g] ELIO". Up to three are named;
 * from four on, two are named, then "& twelve others", five glyph marks and
 * "+7". A pebble with no one draws no line at all.
 */
export function PeopleLine({ soulIds, order, soulMap, markMap, variant, className }: PeopleLineProps) {
  const t = useTranslations("path.spine")
  const { locale } = useLocale()
  const line = peopleLine(soulIds, order, locale)
  if (line.named.length === 0) return null

  const card = variant === "card"
  const markOf = (id: string) => {
    const soul = soulMap.get(id)
    return soul ? markMap.get(soul.glyph_id) : undefined
  }
  const soul = (id: string) => (
    <span key={id} className="inline-flex items-center whitespace-nowrap">
      <SoulMark
        mark={markOf(id)}
        className={cn(
          card
            ? "size-[calc(var(--u)*4)] text-(--spine-ink)"
            : "mr-[.18em] ml-[.08em] size-[1.25em] align-[-.33em] text-(--spine-ink-2)",
        )}
      />
      <span
        className={cn(
          "font-spine-sc lowercase text-(--spine-ink)",
          card
            ? "text-[length:calc(var(--u)*2.5)] font-[650] tracking-[.12em]"
            : "text-[13px] font-[620] tracking-[.1em] spread:text-[13.5px]",
        )}
      >
        {soulMap.get(id)?.name ?? ""}
      </span>
    </span>
  )
  const marks = line.marks.length > 0 && (
    <span aria-hidden className="inline-flex items-center whitespace-nowrap">
      {line.marks.map((id) => (
        <SoulMark
          key={id}
          mark={markOf(id)}
          className={card ? "mx-[.02em] size-[calc(var(--u)*4)] text-(--spine-ink)" : "mx-[.02em] size-[1.25em] align-[-.33em] text-(--spine-ink-2)"}
        />
      ))}
      {line.overflow > 0 && (
        <span
          className={cn(
            "ml-[.25em] font-spine-office font-[560] text-(--spine-ink-2) tabular-nums",
            card ? "text-[length:calc(var(--u)*2.5)]" : "text-[12.5px]",
          )}
        >
          +{line.overflow}
        </span>
      )}
    </span>
  )
  const others = line.others > 0 && (
    <span className="italic font-[380]">{t("others", { words: line.othersWords, count: line.others })}</span>
  )

  if (card) {
    return (
      <p
        className={cn(
          "mt-[calc(var(--u)*1.8)] flex flex-wrap items-center justify-center gap-x-[calc(var(--u)*2.2)] gap-y-[calc(var(--u)*.6)] text-[length:calc(var(--u)*2.6)] leading-[1.2] text-(--spine-ink-2)",
          className,
        )}
      >
        <span className="sr-only">{t("with")} </span>
        {line.named.map(soul)}
        {others}
        {marks}
      </p>
    )
  }

  // "A", "A & B", "A, B & C", or "A, B & twelve others".
  const last = line.others > 0 ? -1 : line.named.length - 1
  return (
    <p className={cn("mt-1.5 text-[15px] leading-normal text-(--spine-ink-2) spread:text-base", className)}>
      <i className="font-[380]">{t("with")}</i>{" "}
      {line.named.map((id, i) => (
        <Fragment key={id}>
          {i > 0 && (i === last ? <span className="font-[380]"> & </span> : ", ")}
          {soul(id)}
        </Fragment>
      ))}
      {others && <> {others}</>} {marks}
    </p>
  )
}
