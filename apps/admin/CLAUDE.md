# @pbbls/admin

Internal admin app for analytics, log management, and feature/announcement publishing.

## Analytics conventions

When adding a metric to `/analytics`, follow these patterns:

- **Restate every headline figure as a sentence in a Tooltip.** Wrap the value
  with `Tooltip` / `TooltipTrigger` / `TooltipContent` from
  `@/components/ui/tooltip`, give the trigger `cursor-help` + a dotted
  underline, and write a complete sentence that names the period, the
  population, and the unit — e.g. `"Last week, the 12 active users owned an
  average of 2.34 glyphs each."` See `components/analytics/UserAverages.tsx`
  for the reference implementation. The number alone is ambiguous; the
  sentence carries the semantics that the column header had to truncate.
- **One Server Component card per surface.** Pattern:
  `XCard.tsx` (SC, awaits the fetcher, renders skeleton/error/empty)
  → `X.tsx` (presentational, may be SC or Client) → `XChart.tsx` (Client when
  it owns interactive state like a metric toggle). See
  `PebbleEnrichmentCard` / `PebbleEnrichment` and
  `UserAveragesCard` / `UserAverages` / `UserAveragesChart`.
- **Always add a `__fixtures__/<surface>.ts` file and a section in
  `app/(authed)/playground/analytics/page.tsx`** with dense / sparse / empty
  variants. The playground is how we review states without seeded data.
- **Fetchers throw `new Error(error.message)`** so consumers can render the
  shared `ErrorBlock` (which does `err instanceof Error ? err.message :
  String(err)`). PostgrestError is not an Error subclass.
- **All analytics data comes through SECURITY DEFINER RPCs** that gate on
  `is_admin(auth.uid())`. Views are revoked from `anon` / `authenticated`.
