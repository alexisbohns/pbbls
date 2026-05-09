"use client"

import { useCallback } from "react"
import { useLocale } from "./useLocale"

/**
 * Format a date using `Intl.DateTimeFormat` for the given locale.
 * Mirrors iOS's `Locale.current` behavior — never hardcode `en-US`.
 */
export function formatDate(
  date: Date | string | number,
  locale: string,
  options?: Intl.DateTimeFormatOptions,
): string {
  const value = date instanceof Date ? date : new Date(date)
  return new Intl.DateTimeFormat(locale, options).format(value)
}

export function formatNumber(
  value: number,
  locale: string,
  options?: Intl.NumberFormatOptions,
): string {
  return new Intl.NumberFormat(locale, options).format(value)
}

export function useFormatDate() {
  const { locale } = useLocale()
  return useCallback(
    (date: Date | string | number, options?: Intl.DateTimeFormatOptions) =>
      formatDate(date, locale, options),
    [locale],
  )
}

export function useFormatNumber() {
  const { locale } = useLocale()
  return useCallback(
    (value: number, options?: Intl.NumberFormatOptions) =>
      formatNumber(value, locale, options),
    [locale],
  )
}

/** Time only, locale-aware (e.g. "2:30 PM" / "14:30"). */
export function useFormatTime() {
  const fmt = useFormatDate()
  return useCallback(
    (date: Date | string | number) =>
      fmt(date, { hour: "numeric", minute: "numeric" }),
    [fmt],
  )
}

/**
 * Peek date format: "SUNDAY 5 APRIL · 10:00".
 * Day/month words from `Intl.DateTimeFormat`; time stays 24h regardless of
 * locale to match the existing visual identity.
 */
export function useFormatPeekDate() {
  const { locale } = useLocale()
  return useCallback(
    (date: Date | string | number) => {
      const value = date instanceof Date ? date : new Date(date)
      const parts = new Intl.DateTimeFormat(locale, {
        weekday: "long",
        day: "numeric",
        month: "long",
      }).formatToParts(value)

      const weekday = parts.find((p) => p.type === "weekday")?.value ?? ""
      const day = parts.find((p) => p.type === "day")?.value ?? ""
      const month = parts.find((p) => p.type === "month")?.value ?? ""

      const hours = String(value.getHours()).padStart(2, "0")
      const minutes = String(value.getMinutes()).padStart(2, "0")

      return `${weekday} ${day} ${month} · ${hours}:${minutes}`.toUpperCase()
    },
    [locale],
  )
}
