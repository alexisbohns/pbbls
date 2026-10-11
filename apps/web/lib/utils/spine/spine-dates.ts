// The Spine's dates (#1052), from Intl so weekday and month names need no keys.

/** "mon", "lun": short weekday, lowercase, trailing period stripped (FR "lun."). */
export function shortWeekday(date: Date, locale: string): string {
  return new Intl.DateTimeFormat(locale, { weekday: "short" }).format(date).toLocaleLowerCase(locale).replace(/\.$/, "")
}

/** "Monday, October 5", "lundi 5 octobre": a day's heading and a stone's date. */
export function longDay(date: Date, locale: string): string {
  return new Intl.DateTimeFormat(locale, { weekday: "long", day: "numeric", month: "long" }).format(date)
}

/** "Monday, October 5 – Sunday, October 11": the span a week's feed covers. */
export function dayRange(from: Date, to: Date, locale: string): string {
  return new Intl.DateTimeFormat(locale, { weekday: "long", day: "numeric", month: "long" }).formatRange(from, to)
}
