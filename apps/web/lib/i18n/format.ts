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
