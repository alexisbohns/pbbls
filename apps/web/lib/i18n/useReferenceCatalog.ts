"use client"

import { useTranslations } from "next-intl"
import type { Emotion } from "@/lib/config/emotions"
import type { Domain } from "@/lib/config/domains"
import type { PebbleShape } from "@/lib/config/pebble-shapes"

type LocalizedEntry = { name: string; label: string }

/**
 * Resolve an emotion's display name & description for the active locale.
 *
 * Lookup happens by `slug` against the `emotion.<slug>` namespace. If no
 * catalog entry exists (e.g. a slug added server-side before the web catalog
 * catches up), we fall back to the DB `name`/`label` carried on the entity —
 * same contract as iOS (`Emotion+Localized.swift`).
 */
export function useEmotionLocalized(
  emotion: Pick<Emotion, "slug" | "name" | "label">,
): LocalizedEntry {
  // Untyped namespace because slugs are runtime DB values, not part of the
  // typed message tree.
  const t = useTranslations() as unknown as {
    (key: string): string
    has(key: string): boolean
  }
  const nameKey = `emotion.${emotion.slug}.name`
  const labelKey = `emotion.${emotion.slug}.label`
  return {
    name: t.has(nameKey) ? t(nameKey) : emotion.name,
    label: t.has(labelKey) ? t(labelKey) : emotion.label,
  }
}

/**
 * Resolve a domain's display name & description for the active locale.
 * Falls back to the DB `name`/`label` when the slug is missing from the
 * catalog — symmetrical to iOS `Domain+Localized.swift`.
 */
export function useDomainLocalized(
  domain: Pick<Domain, "slug" | "name" | "label">,
): LocalizedEntry {
  const t = useTranslations() as unknown as {
    (key: string): string
    has(key: string): boolean
  }
  const nameKey = `domain.${domain.slug}.name`
  const labelKey = `domain.${domain.slug}.label`
  return {
    name: t.has(nameKey) ? t(nameKey) : domain.name,
    label: t.has(labelKey) ? t(labelKey) : domain.label,
  }
}

/**
 * Resolve an emotion category's display name for the active locale.
 * Falls back to the DB `name` column when the slug isn't in the catalog —
 * mirrors `useEmotionLocalized` for the parent grouping.
 */
export function useEmotionCategoryName(
  category: { slug: string; name: string },
): string {
  const t = useTranslations() as unknown as {
    (key: string): string
    has(key: string): boolean
  }
  const key = `emotionCategory.${category.slug}.name`
  return t.has(key) ? t(key) : category.name
}

/**
 * Resolve a pebble shape's display name for the active locale.
 * Falls back to the static `name` when the slug isn't in the catalog.
 */
export function useShapeName(shape: Pick<PebbleShape, "slug" | "name">): string {
  const t = useTranslations() as unknown as {
    (key: string): string
    has(key: string): boolean
  }
  const key = `shape.${shape.slug}.name`
  return t.has(key) ? t(key) : shape.name
}
