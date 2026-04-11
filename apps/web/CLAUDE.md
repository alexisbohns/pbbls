# @pbbls/web

The main Pebbles web application — a local-first PWA built with Next.js (App Router).

## Next.js version warning

This project uses **Next.js 16.2.0** which has breaking changes from earlier versions. APIs, conventions, and file structure may differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` before writing any code. Heed deprecation notices.

## Tech stack

| Layer | Choice |
|-------|--------|
| Framework | Next.js 16.2.0 (App Router) |
| React | 19.2.4 |
| Styling | Tailwind CSS 4 via PostCSS |
| UI library | shadcn/ui (base-nova style, RSC, CSS variables, Lucide icons) |
| Animation | Framer Motion 12 |
| PWA | Serwist 9.5.7 (service worker) |
| Theming | next-themes (light/dark via CSS variables) |
| Date utils | date-fns |
| Icons | Lucide React |
| Canvas | Rive (@rive-app/react-canvas) |
| Markdown | unified + remark-parse + remark-rehype + rehype-stringify |

## Commands

| Command | Description |
|---------|-------------|
| `npm run dev` | Start dev server with HTTPS (`next dev --experimental-https`) |
| `npm run build` | Production build (`next build`) |
| `npm run start` | Start production server (`next start`) |
| `npm run lint` | Run ESLint (flat config, ESLint 9) |
| `npm run generate:splash` | Generate iOS splash screens from config |

Run commands from the **repo root** — Turborepo delegates to workspaces automatically.

## Path aliases

`@/*` resolves to `./` relative to `apps/web/`. Use `@/components`, `@/lib`, `@/app`, etc.

## Directory structure

```
app/              → Route pages (thin shells that import feature components)
components/
  ui/             → shadcn/ui primitives + custom UI components (BackPath, ConfirmDialog, SearchableList, etc.)
  layout/         → Providers (DataProvider, AuthProvider, ThemeProvider, ColorWorldProvider), navigation (BottomNav, Sidebar, MobileHeader), page shell (PageLayout)
  {feature}/      → Domain components by feature: path/, pebble/, record/, souls/, collections/, glyphs/, carve/, profile/, onboarding/, auth/, landing/, docs/
lib/
  types.ts        → Domain entity types (Pebble, Soul, Collection, Mark, Account, Profile, Session)
  config/         → Static configs — imported directly, NOT through the provider
  data/           → DataProvider interface, SupabaseProvider implementation, data hooks (usePebbles, useSouls, useCollections, useMarks, useKarma, useBounce, etc.)
  hooks/          → Non-data hooks: UI logic, interaction, locale (useStepNavigation, useHaptics, useCombobox*, usePebbleVisual, etc.)
  engine/         → Glyph generation engine (rendering, templates, params)
  utils/          → Utility functions (formatters, group-pebbles-by-date, image-compress, simplify-path)
  seed/           → Seed/fixture data for development
  supabase/       → Supabase client initialization (browser + server)
docs/             → Documentation content (markdown pages with i18n, Arkaik architecture bundle)
public/           → PWA manifest, app icons, iOS splash screens
scripts/          → Build scripts (splash screen generation)
```

## Data layer

- **Data hooks** live in `lib/data/` — they wrap the `DataProvider` interface: `usePebbles`, `useSouls`, `useCollections`, `useMarks`, `useKarma`, `useBounce`, `useLookupMaps`, `useSupabaseAuth`, `useReset`.
- **Non-data hooks** live in `lib/hooks/` — UI logic with no data dependency: `useStepNavigation`, `useHaptics`, `useComboboxFilter`, `usePebbleVisual`, etc.
- Components never call the provider directly — always go through hooks.
- **Static configs** (emotions, domains, card types, color worlds, pebble shapes, positiveness, navigation) live in `lib/config/` and are imported directly — they do not go through the provider.
- **Business logic** for gamification lives in `lib/data/bounce-levels.ts` (streak mechanic) and `lib/data/karma.ts` (karma computation). Both are pure functions.
- **Providers**: `SupabaseProvider` is the sole data source, backed by Supabase. `DataProvider.tsx` wraps it and exposes data through hooks.

## Component patterns

- Add new shadcn/ui components with: `npx shadcn@latest add <component>`
- shadcn config is in `components.json` (base-nova style, RSC enabled, CSS variables, Lucide icons).
- Custom UI primitives (`BackPath`, `ConfirmDialog`, `EmotionBadge`, `SearchableList`, `SelectableItem`, `TagList`) live alongside shadcn in `components/ui/`.
- Feature components are organized by domain folder: `path/`, `pebble/`, `record/`, `souls/`, `collections/`, `glyphs/`, `carve/`, `profile/`, `onboarding/`, `auth/`, `landing/`, `docs/`.
- Route pages in `app/` are thin shells — they import and render a feature component.

## PWA / Service Worker

- Service worker is managed by Serwist — configured in `next.config.ts`.
- SW source file: `app/sw.ts` (excluded from main `tsconfig.json`, has its own `tsconfig.sw.json`).
- Registration component: `components/layout/SerwistRegistration.tsx`.
- Generated output: `public/sw.js` — do not edit manually.

## Things to avoid

- Do not import from provider files directly in components — use data hooks.
- Do not add CSS files — use Tailwind utilities and CSS variables in `app/globals.css`.
- Do not modify `public/sw.js` — it is auto-generated by Serwist.
- Do not put data-fetching hooks in `lib/hooks/` — those belong in `lib/data/`.
- Do not put domain entity types in component files — define them in `lib/types.ts`.
