# Remote settings links and app licences — design

- **Date:** 2026-11-01
- **Issues:** #1489 (back-end + admin); web, iOS and Android issues filed with the plan. Licences close the native half of #1395.
- **Milestone:** M68 · V1 launch
- **Follows:** #1484 (docs site in M3, OFL-compliant credits page)

## Goal

The maintainer adds, edits or removes the links shown in every client's Settings (terms, privacy, credits, support…) from the admin, and every surface follows without a release. Separately, each native app carries the licences of what it bundles; iOS shows them in the system Settings app, the way most iOS apps do.

## Decisions

| # | Decision |
|---|---|
| D1 | A row may point at **any URL**: `https://` (opened in the in-app browser) or `mailto:` (handed to the OS). No other scheme. |
| D2 | The remote list drives **only the Settings group** on web, iOS and Android. Sign-up, welcome, the consent gate and web re-consent keep their built-in Terms/Privacy URLs: consent is bound to a document version and must never depend on admin data. |
| D3 | Storage is a **dedicated typed table**, `settings_links`, modelled on `guides` (20261027090000). Not a generic key/value config (no other remote flag exists; YAGNI), not a static JSON on the web (every change would need a deploy). |
| D4 | **iOS licences live in a `Settings.bundle`**: Settings → Pebbles → Licenses → one pane per component with its licence text, and a copyright footer. Static by nature (the system Settings app cannot fetch or open links), generated at build time. |
| D5 | **Android has no native licences screen.** Store policy does not require one; the licences do require that notices travel with the copy. Android bundles the font `OFL.txt` files in the APK assets, and its users see licences through the remote "Credits" row opening `/docs/credits`. |
| D6 | `/docs/credits` lists the **libraries the apps ship** (not only fonts), so the browser path is complete for Android and useful for everyone. |

## 1. Data — `settings_links`

```sql
create table public.settings_links (
  id          uuid primary key default gen_random_uuid(),
  key         text not null unique check (key ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
  section     text not null check (section in ('legal','about','support')),
  sort_order  smallint not null default 0,
  title_en    text not null,
  title_fr    text,
  url         text not null check (url ~ '^(https://|mailto:)'),
  published   boolean not null default false,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);
```

- `set_updated_at` trigger; RLS identical to `guides`: select `published = true or is_admin(auth.uid())`, insert/update/delete admin only. Index on `(published, section, sort_order)`.
- Not user data: no `purge_account` append. No emotion or domain inserted: no `sync_achievement_catalog()`.
- Section order is fixed in the clients: `legal`, `about`, `support`. Section headers are client strings (EN/FR), not data.
- Seed (deterministic ids, `md5('settings_links:<key>')::uuid`, published), matching what Settings shows today plus the launch inventory's needs:

| key | section | order | title EN / FR | url |
|---|---|---|---|---|
| terms | legal | 10 | Terms of Service / Conditions générales d'utilisation | https://www.pbbls.app/docs/terms |
| privacy | legal | 20 | Privacy Policy / Politique de confidentialité | https://www.pbbls.app/docs/privacy |
| legal-notice | legal | 30 | Legal Notice / Mentions légales | https://www.pbbls.app/docs/legal-notice |
| credits | about | 10 | Credits / Crédits | https://www.pbbls.app/docs/credits |
| support | support | 10 | Contact support / Contacter le support | mailto:ciao@pbbls.app |

The support row moves to `https://www.pbbls.app/support` from the admin once that page ships; no client change.

## 2. Admin — `/settings-links`

A copy of the Guides admin (`apps/admin/app/(authed)/guides/`): a table grouped by section (title, URL, order, published), a create/edit form (key, section, order, EN title required, FR title optional, URL, published) and a delete button with confirmation. Server actions mirror `guides/actions.ts`. The URL is validated client-side with the same `https://|mailto:` rule; the database check is the authority. A nav entry next to Guides.

## 3. Clients — web, iOS, Android (mirrored)

**Read.** A single-table select of published rows: `from("settings_links").select("key,section,sort_order,title_en,title_fr,url").eq("published", true).order("section").order("sort_order")` (no RPC: single table, single statement). The client re-sorts sections into the fixed order and drops a section it does not know (forward compatible). Title = FR when the app is in French and `title_fr` is non-null, else EN.

**Cache and fallback.** Each client ships a built-in default list equal to the seed and keeps the last successful payload (web: `localStorage`; iOS: `UserDefaults` JSON; Android: DataStore JSON). Settings renders cache-or-default immediately and refreshes on open; a failed fetch keeps what is shown and logs (web: `console.error` with context; native: the surface's logger). No spinner, no empty state, works offline. An empty successful payload is treated as data (the maintainer unpublished everything), not as failure.

**Open.** `https://` → web: new tab (`target="_blank" rel="noopener noreferrer"`); iOS: the existing `LegalDocumentSheet` (`SFSafariViewController`); Android: the existing Custom Tab helper, generalised from `LegalDoc` to a URL. `mailto:` → the OS (`window.location`, `openURL`, `ACTION_SENDTO`). Any other scheme received is dropped, never opened.

**Replaces.** web `components/settings/LegalSection.tsx`; iOS the Legal group in `SettingsSheet.swift`; Android `SettingsAccountSections`' Legal `ListGroup`. The funnel call sites (`AuthView`, `WelcomeView`, `ConsentGateScreen`, `register/page.tsx`, `ReconsentScreen`) are untouched (D2). Android's `LegalDoc` enum stays for them.

## 4. Licences

**iOS — `Settings.bundle`.** `Root.plist`: a group "Pebbles" with a child pane "Licenses" and the footer `Copyright © 2026 Pebbles`. `Licenses.plist`: one child pane per component, sorted by name; each pane is a group whose footer text is the full licence. Components: the four fonts (Ysabeau, Inclusive Sans, Caveat, Reenie Beanie — their `OFL.txt` with copyright line, all four bundled under `Resources/Licenses/`) and every Swift package in `Package.resolved` with its licence file. A script (`apps/ios/scripts/generate-licenses.*`) builds the plists from those sources; it runs by hand and in CI with `--check`, failing when the committed plists are stale or a resolved package has no licence entry. The bundle is added in `project.yml` (xcodegen).

**Android.** The four `OFL.txt` files in `app/src/main/assets/licenses/`. No screen (D5).

**Web — `/docs/credits` v1.3.0.** A "Libraries" section listing, per surface, the shipped libraries and their licence (iOS Swift packages; the main Gradle dependencies: AndroidX/Compose, Supabase-kt, Ktor, Coil, Rive; the main npm packages beyond React), each with its copyright holder and licence name. EN and FR.

## Testing

- **Database:** a contract harness `verify-settings-links.ts` (anon-only, so it joins the CI gate): anon sees published rows only; anon insert/update/delete refused; a non-`https`/`mailto` URL refused by the check.
- **Clients:** each decodes the seed payload as the database returns it (including `title_fr: null`), per the foreign-payload rule; the built-in default equals the seed (a test per surface reads the seed's values); unknown sections and schemes are dropped; FR falls back to EN.
- **iOS licences:** the generator's `--check` mode in CI; a unit test that every `Package.resolved` identity has a pane.
- **Admin:** the form's URL validation.

## Out of scope

Remote control of the funnel's consent links (D2); a native Android licences screen (D5); icons per row; per-platform targeting of rows (every row shows on every surface; add a `platforms` column only when a real need appears); the `/support` page itself (its own issue).

## Delivery

One `gh stack`, M68, in dependency order:

1. **db** — migration, seed, regenerated `database.ts`, harness (#1489).
2. **admin** — `/settings-links` (#1489).
3. **web** — Settings section from the table + credits libraries list.
4. **ios-links** — Settings group from the table.
5. **ios-licences** — `Settings.bundle`, generator, bundled `OFL.txt` (part of #1395).
6. **android** — Settings group from the table + bundled `OFL.txt` (rest of #1395).

Native branch slugs carry `-ios` / `-android`.
