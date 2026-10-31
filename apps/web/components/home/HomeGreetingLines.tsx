import { useTranslations } from "next-intl"
import { dateline, greetingKey, greetingTitle } from "@/lib/home/greeting"

type HomeGreetingLinesProps = {
  now: Date
  name: string | null | undefined
  locale: string
  timeZone: string
}

/** The date line, the greeting (the page's `<h1>`) and its italic line (#1384; iOS `HomeGreeting`). */
export function HomeGreetingLines({ now, name, locale, timeZone }: HomeGreetingLinesProps) {
  const t = useTranslations("home.greeting")
  const key = greetingKey(now, timeZone)
  return (
    <div>
      <p className="pb-type-date text-m3-primary">{dateline(now, locale, timeZone)}</p>
      <h1 className="pb-type-greeting mt-2 text-m3-on-surface">{greetingTitle(t(`${key}.title`), name)}</h1>
      <p className="pb-type-lead mt-2 text-m3-on-surface-variant">{t(`${key}.line`)}</p>
    </div>
  )
}
