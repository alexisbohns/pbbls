import type { LogPlatform, LogSpecies, LogStatus } from "./types"

export const SPECIES_OPTIONS: ReadonlyArray<{ value: LogSpecies; label: string }> = [
  { value: "announcement", label: "Announcement" },
  { value: "feature", label: "Feature" },
]

export const PLATFORM_OPTIONS: ReadonlyArray<{ value: LogPlatform; label: string }> = [
  { value: "all", label: "All platforms" },
  { value: "web", label: "Web" },
  { value: "ios", label: "iOS" },
  { value: "android", label: "Android" },
]

export const STATUS_OPTIONS: ReadonlyArray<{ value: LogStatus; label: string }> = [
  { value: "backlog", label: "Backlog" },
  { value: "planned", label: "Planned" },
  { value: "in_progress", label: "In progress" },
  { value: "shipped", label: "Shipped" },
]

const SPECIES_VALUES = SPECIES_OPTIONS.map((o) => o.value)
const STATUS_VALUES = STATUS_OPTIONS.map((o) => o.value)

export function isLogSpecies(value: string | undefined): value is LogSpecies {
  return value !== undefined && (SPECIES_VALUES as readonly string[]).includes(value)
}

export function isLogStatus(value: string | undefined): value is LogStatus {
  return value !== undefined && (STATUS_VALUES as readonly string[]).includes(value)
}

export type PlatformFilter = "web" | "ios" | "android"

export const PLATFORM_FILTER_OPTIONS: ReadonlyArray<{ value: PlatformFilter; label: string }> = [
  { value: "web", label: "Web" },
  { value: "ios", label: "iOS" },
  { value: "android", label: "Android" },
]

const PLATFORM_FILTER_VALUES = PLATFORM_FILTER_OPTIONS.map((o) => o.value)

export function isPlatformFilter(value: string | undefined): value is PlatformFilter {
  return value !== undefined && (PLATFORM_FILTER_VALUES as readonly string[]).includes(value)
}
