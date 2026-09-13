import { describe, expect, it } from "vitest"
import { PLATFORM_OPTIONS, SPECIES_OPTIONS, STATUS_OPTIONS } from "./options"
import { parseLabNoteYaml } from "./parse-lab-note"

// The parser is the only thing standing between a PR's Lab Note and a published
// Lab entry: whatever it drops, the maintainer has to notice by eye in the form.
// The FR half is the easy one to lose silently, so the bilingual block gets the
// most attention here.

// The shape authored in a PR body, once the skeleton's `#` comments are gone.
const PR_NOTE = `species: feature
platform: ios
status: in_progress
published: false
en:
  title: "Draft a pebble, finish it later"
  summary: "Start a pebble, wander off, come back. It is still there."
fr:
  title: "Commence un caillou, finis-le plus tard"
  summary: "Tu commences un caillou, tu pars ailleurs, il t'attend."
nodes: [V-pebble-record, F-record-pebble-flow]
`

describe("parseLabNoteYaml", () => {
  // The verbatim skeleton from CLAUDE.md's "Lab Note requirement" section,
  // trailing `#` comments and all. This is what an author actually copies, so
  // it is the one input the parser cannot afford to mangle.
  it("reads the CLAUDE.md skeleton verbatim, comments and all", () => {
    const parsed = parseLabNoteYaml(`species: feature          # announcement | feature
platform: ios             # all | webapp | ios | android | project | infra
status: in_progress       # backlog | planned | in_progress | shipped
published: false
en:
  title: "Short, benefit-first title"
  summary: "One or two sentences, user-facing."
fr:
  title: "Titre court, oriente benefice"
  summary: "Une ou deux phrases, adaptees."
`)
    expect(parsed?.species).toBe("feature")
    expect(parsed?.platform).toBe("ios")
    expect(parsed?.status).toBe("in_progress")
    expect(parsed?.published).toBe(false)
  })

  it("strips a comment from an unquoted value without touching a quoted one", () => {
    const parsed = parseLabNoteYaml(`species: feature   # a comment
en:
  title: "A title # with a hash in it"
  summary: Unquoted summary   # trailing note
`)
    expect(parsed?.species).toBe("feature")
    expect(parsed?.title_en).toBe("A title # with a hash in it")
    expect(parsed?.summary_en).toBe("Unquoted summary")
  })

  // YAML starts a comment only at a `#` that follows whitespace, so a hash glued
  // to the text is part of the value. A space before it makes it a comment, in
  // the parser as in YAML — quoting is how you keep one (see the case above).
  it("keeps a hash that is not preceded by whitespace", () => {
    const parsed = parseLabNoteYaml(`species: feature
en:
  title: Issue#827 shipped
  summary: Ships in v2 # and this part is a comment
`)
    expect(parsed?.title_en).toBe("Issue#827 shipped")
    expect(parsed?.summary_en).toBe("Ships in v2")
  })

  it("drops a whole-line comment", () => {
    const parsed = parseLabNoteYaml(`# this is a Lab Note
species: feature
en:
  title: "A title"
`)
    expect(parsed?.species).toBe("feature")
    expect(parsed?.title_en).toBe("A title")
  })

  it("reads every field of a PR-shaped note, both languages", () => {
    expect(parseLabNoteYaml(PR_NOTE)).toEqual({
      species: "feature",
      platform: "ios",
      status: "in_progress",
      published: false,
      title_en: "Draft a pebble, finish it later",
      summary_en: "Start a pebble, wander off, come back. It is still there.",
      title_fr: "Commence un caillou, finis-le plus tard",
      summary_fr: "Tu commences un caillou, tu pars ailleurs, il t'attend.",
    })
  })

  it("reads the list-of-single-key-maps form the issue template uses", () => {
    const parsed = parseLabNoteYaml(`- species: announcement
- platform: all
- en:
    title: "Pebbles is open"
    summary: "Anyone can sign up now."
- fr:
    title: "Pebbles ouvre"
    summary: "Tu peux t'inscrire."
`)
    expect(parsed).toEqual({
      species: "announcement",
      platform: "all",
      title_en: "Pebbles is open",
      summary_en: "Anyone can sign up now.",
      title_fr: "Pebbles ouvre",
      summary_fr: "Tu peux t'inscrire.",
    })
  })

  // The reason CLAUDE.md insists every title and summary is double-quoted: a
  // colon is the natural way to write one, and it must land in the value, not
  // split the line a second time.
  it("keeps a colon inside a title or summary", () => {
    const parsed = parseLabNoteYaml(`species: feature
en:
  title: "Heads up: it moved"
  summary: "The button is now here: bottom right."
`)
    expect(parsed?.title_en).toBe("Heads up: it moved")
    expect(parsed?.summary_en).toBe("The button is now here: bottom right.")
  })

  it("strips matching single or double quotes, and leaves an unquoted value alone", () => {
    const parsed = parseLabNoteYaml(`species: feature
en:
  title: 'Single quoted'
  summary: Unquoted summary
`)
    expect(parsed?.title_en).toBe("Single quoted")
    expect(parsed?.summary_en).toBe("Unquoted summary")
  })

  // A stray `title:` outside any en/fr block must not become the EN title by
  // accident — without a section it falls through to the switch's default.
  it("ignores a title that is not nested under en or fr", () => {
    expect(
      parseLabNoteYaml(`species: feature
title: "Homeless title"
`),
    ).toBeNull()
  })

  it("closes the bilingual block at the next top-level key", () => {
    const parsed = parseLabNoteYaml(`en:
  title: "The title"
  summary: "The summary."
platform: webapp
  title: "Not the FR title"
`)
    expect(parsed?.platform).toBe("webapp")
    expect(parsed?.title_fr).toBeUndefined()
  })

  it("drops an unknown enum value rather than failing the whole note", () => {
    const parsed = parseLabNoteYaml(`species: bugfix
platform: smartwatch
status: almost
en:
  title: "Still readable"
  summary: "The recognized fields survive."
`)
    expect(parsed).toEqual({
      title_en: "Still readable",
      summary_en: "The recognized fields survive.",
    })
  })

  it("reads `published` as true only for the literal true, case-insensitively", () => {
    const withPublished = (value: string) =>
      parseLabNoteYaml(`published: ${value}
en:
  title: "T"
`)?.published
    expect(withPublished("true")).toBe(true)
    expect(withPublished("TRUE")).toBe(true)
    expect(withPublished("false")).toBe(false)
    expect(withPublished("yes")).toBe(false)
  })

  it("accepts all three spellings of the release date and stores an instant", () => {
    for (const key of ["release-date", "release_date", "released_at"]) {
      const parsed = parseLabNoteYaml(`${key}: 2026-07-17T20:00:00Z
en:
  title: "T"
`)
      expect(parsed?.released_at).toBe("2026-07-17T20:00:00.000Z")
    }
  })

  it("drops an unparseable release date", () => {
    const parsed = parseLabNoteYaml(`release-date: sometime next week
species: feature
en:
  title: "T"
`)
    expect(parsed?.released_at).toBeUndefined()
    expect(parsed?.species).toBe("feature")
  })

  // Arbitrary clipboard text must open a blank form, not a half-filled one.
  it("returns null for text that is not a Lab Note", () => {
    expect(parseLabNoteYaml("")).toBeNull()
    expect(parseLabNoteYaml("   \n\n ")).toBeNull()
    expect(parseLabNoteYaml("just some copied prose, no colons at all")).toBeNull()
    expect(parseLabNoteYaml("https://github.com/alexisbohns/pbbls/pull/827")).toBeNull()
  })

  it("requires an EN title plus one more recognized field", () => {
    expect(
      parseLabNoteYaml(`en:
  title: "Alone"
`),
    ).toBeNull()
    expect(
      parseLabNoteYaml(`species: feature
en:
  title: "Alone"
`)?.title_en,
    ).toBe("Alone")
  })

  it("returns null when only the FR half is present, so the form is not half-filled", () => {
    expect(
      parseLabNoteYaml(`species: feature
fr:
  title: "Seulement en français"
  summary: "Pas de version anglaise."
`),
    ).toBeNull()
  })

  it("tolerates CRLF line endings, as pasted from a browser", () => {
    const parsed = parseLabNoteYaml(PR_NOTE.replace(/\n/g, "\r\n"))
    expect(parsed?.title_fr).toBe("Commence un caillou, finis-le plus tard")
  })

  // The parser keeps its own platform list, separate from the form's options.
  // Two lists of the same enum drift; this is the test that notices.
  it("parses every enum value the form offers", () => {
    for (const { value } of SPECIES_OPTIONS) {
      expect(parseLabNoteYaml(`species: ${value}\nen:\n  title: "T"\n`)?.species).toBe(value)
    }
    for (const { value } of PLATFORM_OPTIONS) {
      expect(parseLabNoteYaml(`platform: ${value}\nen:\n  title: "T"\n`)?.platform).toBe(value)
    }
    for (const { value } of STATUS_OPTIONS) {
      expect(parseLabNoteYaml(`status: ${value}\nen:\n  title: "T"\n`)?.status).toBe(value)
    }
  })
})
