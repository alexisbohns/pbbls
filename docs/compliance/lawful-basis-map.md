# Lawful basis map

**Status:** living reference. Update it in the same PR as any feature that adds
a processing operation. Separate from `dpia.md` so this can move without
re-dating the assessment.

**Last reviewed:** 2026-09-11

| # | Processing operation | Data | Art. 6 basis | Art. 9 condition | Consent record |
|---|---|---|---|---|---|
| 1 | Account creation and authentication | email, password hash, `profiles.display_name` | 6(1)(b) contract | n/a | — |
| 2 | Recording a pebble | `pebbles.intensity`, `positiveness`, `emotion_id`, `description` | 6(1)(a) consent | **9(2)(a) explicit consent** | `user_consents.kind = 'health_data'` — ledger live, *not yet written by any surface* (see gap) |
| 3 | Photo attachments on a pebble | `snaps`, storage objects | 6(1)(a) consent | 9(2)(a), via #2 | as #2 |
| 4 | Naming souls in a pebble | `souls`, `pebble_souls` | 6(1)(f) legitimate interest | n/a (third-party data — see DPIA §3) | — |
| 5 | Reflective cards | `pebble_cards` | 6(1)(a) consent | 9(2)(a), via #2 | as #2 |
| 6 | Mutual connections and connection-visible pebbles | `connections`, `pebbles.visibility` | 6(1)(a) consent | 9(2)(a), via #2 | as #2 |
| 7 | Public profile | `profiles.handle`, `public_profile` | 6(1)(a) consent | 9(2)(a) — enlarges #2 to the open web | `user_consents.kind = 'public_profile'` — ledger live, *not yet written by any surface* (see gap); `profiles.public_profile` remains the only opt-in evidence on disk |
| 8 | Public share-by-link of a pebble | `pebbles.visibility = 'public'` | 6(1)(a) consent | 9(2)(a), via #2 | as #2 |
| 9 | Karma, bounces, achievements | `karma_events`, `bounces`, `achievement_unlocks` | 6(1)(b) contract | n/a | — |
| 10 | Glyph marketplace | `glyphs`, `glyph_submissions`, `glyph_entitlements` | 6(1)(b) contract | n/a | — |
| 11 | Operator analytics | aggregate views, `is_admin`-gated | 6(1)(f) legitimate interest | see gap below | — |
| 12 | Security and abuse prevention | auth logs | 6(1)(f) legitimate interest | n/a | — |

## Known gaps

- **Until `user_consents` landed, no dedicated consent record existed for any
  of the Art. 9(2)(a) conditions claimed above (#2, #3, #5, #6, #8), nor for
  the public-profile opt-in (#7).** `profiles.terms_accepted_at` /
  `profiles.privacy_accepted_at` (persisted by `handle_new_user()`, migration
  `20260729120000_handle_new_user_consent.sql`) record acceptance of the Terms
  and Privacy Policy as a whole, at signup — not a distinct, revocable consent
  to processing mood, emotion and reflection data as special-category health
  data. That is the Kritik finding this map exists to close:
  `F-2026-08-GDP-web-01`.

  **Storage now exists; capture does not yet.** Migrations
  `20260911090000_user_consents.sql` and `20260911090060_user_consents_hardening.sql`
  add the `user_consents` ledger — each consent act recorded against the policy
  version it was given under, revocable through `withdraw_consent`. **No client
  writes it yet**, so as of this review the table is empty and the `user_consents`
  references in the table above describe where the evidence *will* live, not
  evidence held. `handle_new_user` records the email-signup grant only once a
  client sends `health_data_consent_at` / `health_data_consent_version` in its
  signup metadata (web: #775 Tasks 9-10); the public-profile grant needs the
  settings surface (#775 Tasks 16-17). Until those land, `profiles.public_profile`
  is still the only opt-in evidence on disk for #7, and there is none for #2.

  Uncovered even after the web tasks land, and therefore still open: accounts
  that existed before this migration, accounts created through the login page's
  OAuth buttons, and the iOS and Android surfaces (Kritik `F-2026-08-GDP-ios-04`,
  `F-2026-08-GDP-android-02`) — none gets an Art. 9 consent record until the M55
  re-consent surface asks for one.
- **#11 aggregates emotion data with no minimum-cohort threshold.** For a week
  with one or two active users the weekly emotion mix is effectively one
  identifiable person's record. Tracked as Kritik `F-2026-08-GDP-admin-06`.
- **#4 is third-party personal data** the data subject never supplied. See
  DPIA §3 "the souls problem".
- **Art. 20 portability is unmet**: no data export exists on any surface.
