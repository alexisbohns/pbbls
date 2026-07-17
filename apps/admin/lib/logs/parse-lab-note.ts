import { isLogSpecies, isLogStatus } from "./options"
import type { LogPlatform, LogSpecies, LogStatus } from "./types"

/** sessionStorage key used to hand a parsed Lab Note from the "New log" click to the form. */
export const LAB_NOTE_PREFILL_KEY = "labNotePrefill"

const PLATFORM_VALUES: readonly LogPlatform[] = [
  "all",
  "webapp",
  "ios",
  "android",
  "project",
  "infra",
]
function isLogPlatform(value: string): value is LogPlatform {
  return (PLATFORM_VALUES as readonly string[]).includes(value)
}

/** Fields a Lab Note YAML snippet can prefill on the New-log form. */
export type LabNotePrefill = {
  species?: LogSpecies
  platform?: LogPlatform
  status?: LogStatus
  released_at?: string
  published?: boolean
  title_en?: string
  summary_en?: string
  title_fr?: string
  summary_fr?: string
}

function stripQuotes(value: string): string {
  if (value.length >= 2) {
    const first = value[0]
    const last = value[value.length - 1]
    if ((first === '"' && last === '"') || (first === "'" && last === "'")) {
      return value.slice(1, -1)
    }
  }
  return value
}

// Split on the FIRST colon only, so datetimes ("2026-07-17T20:00:00") and
// colons inside a title/summary survive in the value.
function splitKeyValue(content: string): [string, string] | null {
  const idx = content.indexOf(":")
  if (idx === -1) return null
  const key = content.slice(0, idx).trim().toLowerCase()
  if (!key) return null
  const value = stripQuotes(content.slice(idx + 1).trim())
  return [key, value]
}

/**
 * Parse a Lab Note YAML snippet (copied from a PR) into a partial that prefills
 * the New-log form. Tolerant of both the idiomatic nested form and the issue's
 * list-of-single-key-maps form (leading `- `). Unknown enum values are dropped
 * rather than fatal. Returns null when the text doesn't look like a Lab Note, so
 * a non-matching clipboard simply opens a blank form.
 */
export function parseLabNoteYaml(text: string): LabNotePrefill | null {
  if (!text || !text.trim()) return null

  const result: LabNotePrefill = {}
  let matched = 0
  let section: "en" | "fr" | null = null
  let sectionIndent = 0

  for (const rawLine of text.split(/\r?\n/)) {
    if (!rawLine.trim()) continue
    const indent = rawLine.length - rawLine.trimStart().length
    let content = rawLine.trimStart()
    // Strip a YAML list marker ("- species: feature" / "- en:").
    if (content === "-") continue
    if (content.startsWith("- ")) content = content.slice(2)

    const kv = splitKeyValue(content)
    if (!kv) continue
    const [key, value] = kv

    // A bare `en:` / `fr:` opens a bilingual block for the lines nested under it.
    if ((key === "en" || key === "fr") && value === "") {
      section = key
      sectionIndent = indent
      continue
    }

    // title/summary belong to the most recent en/fr block when nested under it.
    if ((key === "title" || key === "summary") && section && indent > sectionIndent) {
      if (!value) continue
      if (key === "title") {
        if (section === "en") result.title_en = value
        else result.title_fr = value
      } else {
        if (section === "en") result.summary_en = value
        else result.summary_fr = value
      }
      matched++
      continue
    }

    // Any recognized top-level key closes an open bilingual block.
    section = null

    switch (key) {
      case "species":
        if (isLogSpecies(value)) {
          result.species = value
          matched++
        }
        break
      case "platform":
        if (isLogPlatform(value)) {
          result.platform = value
          matched++
        }
        break
      case "status":
        if (isLogStatus(value)) {
          result.status = value
          matched++
        }
        break
      case "release-date":
      case "release_date":
      case "released_at": {
        // A naive datetime is read in the admin's local zone (same as the
        // form's datetime-local input); store the ISO instant.
        const d = new Date(value)
        if (!Number.isNaN(d.getTime())) {
          result.released_at = d.toISOString()
          matched++
        }
        break
      }
      case "published":
        result.published = value.toLowerCase() === "true"
        matched++
        break
      default:
        break
    }
  }

  // Treat it as a Lab Note only with an EN title plus at least one other
  // recognized field — arbitrary clipboard text must not hijack the form.
  if (!result.title_en || matched < 2) return null
  return result
}
