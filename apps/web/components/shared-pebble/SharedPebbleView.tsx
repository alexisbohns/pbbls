"use client"

import Link from "next/link"
import type { CSSProperties } from "react"
import { useTranslations } from "next-intl"
import type { SharedPebble } from "@/lib/types"
import { useFormatDate, useEmotionLocalized } from "@/lib/i18n"

type SharedPebbleViewProps = {
  pebble: SharedPebble
}

/**
 * The /p/[id] public share page body (M51). Anonymous-friendly: renders only
 * the `get_shared_pebble` projection — the server-composed visual, name,
 * emotion and date — plus a "made with Pebbles" CTA back to the landing page.
 * Deliberately owner-less (the projection carries no user identity) and
 * snap-less (excluded from public shares in v1).
 *
 * Not PebbleVisual: that component takes a full owner-side `Pebble` and
 * fetches the palette through the data layer, while the projection already
 * carries the two tints — so the wrapper mirrors PebbleVisual's CSS-variable
 * contract (`.pbbls-visual` + --pebble-stroke-light/dark) without a second
 * query or a session.
 */
export function SharedPebbleView({ pebble }: SharedPebbleViewProps) {
  const t = useTranslations("sharedPebble")
  const formatDate = useFormatDate()
  const { name: emotionName } = useEmotionLocalized({
    slug: pebble.emotion.slug,
    name: pebble.emotion.name,
    label: "",
  })

  // Fall back to the flat emotion color when the category palette is absent —
  // both tints then match, which only costs the dark-mode shade.
  const wrapperStyle = {
    ["--pebble-stroke-light"]: pebble.emotion.primary_color ?? pebble.emotion.color,
    ["--pebble-stroke-dark"]: pebble.emotion.secondary_color ?? pebble.emotion.color,
  } as CSSProperties

  return (
    <div className="mx-auto flex min-h-full w-full max-w-md flex-col px-4 pt-10 md:px-6 md:pt-14">
      <section className="flex flex-col items-center gap-8 text-center">
        <span className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
          {t("overline")}
        </span>

        {pebble.render_svg ? (
          <div
            data-slot="pebble-visual"
            role="img"
            aria-label={t("visualAria", { name: pebble.name, emotion: emotionName })}
            className="pbbls-visual size-40"
            style={wrapperStyle}
            dangerouslySetInnerHTML={{ __html: pebble.render_svg }}
          />
        ) : (
          // Legacy rows that pre-date the remote engine have no composed SVG;
          // an emotion-tinted stone outline keeps the page whole.
          <div
            role="img"
            aria-label={t("visualAria", { name: pebble.name, emotion: emotionName })}
            className="size-40 rounded-full border-[6px]"
            style={{ borderColor: pebble.emotion.color }}
          />
        )}

        <div className="flex flex-col items-center gap-2">
          <h1 className="font-script text-[41px] leading-none tracking-[-0.05em] text-foreground">
            {pebble.name}
          </h1>
          <span className="flex items-center gap-2 text-sm font-medium text-muted-foreground">
            <span
              className="size-2.5 rounded-full"
              style={{ backgroundColor: pebble.emotion.color }}
              aria-hidden
            />
            {emotionName}
            <span aria-hidden>·</span>
            {formatDate(pebble.happened_at, { dateStyle: "medium" })}
          </span>
        </div>

        {pebble.description ? (
          <p className="max-w-prose text-sm leading-relaxed text-foreground">
            {pebble.description}
          </p>
        ) : null}
      </section>

      <footer className="mt-auto flex justify-center py-10">
        <Link
          href="/"
          className="text-sm font-medium text-muted-foreground transition-colors hover:text-foreground"
        >
          {t("madeWith")}
        </Link>
      </footer>
    </div>
  )
}
