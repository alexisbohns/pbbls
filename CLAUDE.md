@AGENTS.md

# Project Guidelines

## Before you start

- Read the README.md to understand the project, its architecture, and its data flow.
- Check the issue description for the specific task and its dependencies.
- Never refactor existing code without explicit approval. If you see something to improve, mention it in a comment — don't change it.

## Principles

### Atomic Design & Separation of Concerns

- Build small, composable, single-responsibility components.
- Each component does one thing. If it needs a second responsibility, split it.
- Avoid duplicating components — reuse and compose instead (ie. a status badge with only an icon and another with icon + text should be the same component with a variant prop).
- UI primitives live in `<app>/components/ui/` (shadcn/ui), one set per app. Do not duplicate them within an app.
- Reach for shadcn first when introducing a layout or interaction pattern (sidebar, breadcrumb, dropdown menu, dialog, sheet, tooltip, command palette, etc.). Do not hand-roll a `<nav>` + `<ul>` when `Sidebar`/`SidebarMenu` exists, or stitch a `<form action="/signout">` button when `DropdownMenu` already does the right thing. Hand-rolling re-implements accessibility, mobile drawer behaviour, keyboard nav, and theme integration that shadcn already solves.
- Domain components live in feature folders: `apps/web/components/path/`, `apps/web/components/pebble/`, `apps/web/components/record/`, etc.
- Hooks encapsulate data access. Components never call the provider directly.
- Sidebar is customizable per page by passing the sidebar prop: `<PageLayout sidebar={<PathProfileCard />}>...</PageLayout>`

### File & Naming Conventions

- Component files: PascalCase (`PebbleCard.tsx`).
- Hooks: camelCase prefixed with `use` (`usePebbles.ts`).
- Config and utility files: kebab-case (`card-types.ts`).
- One exported component per file. Co-locate sub-components only if they are exclusively used by the parent.

### TypeScript

- Strict mode. No `any`. No type assertions unless absolutely necessary.
- Define types in `apps/web/lib/types.ts` for domain entities.
- Props types are defined inline or co-located with the component, not in `types.ts`.

### Accessibility

- All interactive elements must be keyboard-navigable.
- Use semantic HTML elements like `button` for interaction, `nav` for navigation, `main` for main content, `section` for sections, `h1`–`h6` for headings, `ul`/`ol` for lists even if it's a grid of cards, `article` for standalone content (ie. a pebble card), abbreviations like `abbr` for emotion icons, etc.
- Images and icons must have meaningful `alt` text or `aria-label`.
- Use `aria-live` regions for dynamic content updates.
- Color is never the sole indicator — always pair with text or icons.
- Follow WCAG 2.1 AA as the baseline.

### Styling

- Use Tailwind CSS utility classes. Avoid custom CSS unless necessary.
- Use shadcn/ui components as the base. Extend with CVA variants if needed.
- Add primitives via `npx shadcn@latest add <name>` from inside the app folder — never hand-write a primitive that the registry ships.
- Don't surcharge shadcn primitives. If you find yourself appending more than a handful of utility classes, or rewriting the inner markup, you're probably reaching for the wrong primitive — pick a different one rather than fighting the one you have.
- Pair `<Label htmlFor=…>` with the input's `id` (including `<SelectTrigger id=…>`). Don't wrap a Select in a `<label>` — Base UI's hidden-input wiring doesn't auto-associate that way.
- This monorepo's shadcn style is `base-nova`, which sits on `@base-ui/react`, **not** Radix. Consequence: no `asChild` prop on `Button`, `SidebarMenuButton`, `DropdownMenuTrigger`, `BreadcrumbLink`, etc. Use `<Link className={buttonVariants(...)}>` for nav-as-button, and `render={<Link href=…/>}` where the primitive accepts a `render` prop.
- Support light and dark themes via CSS variables. When scaffolding a new app's `globals.css`, copy the **complete** token set from `apps/web/app/globals.css` — including `--popover*` and the eight `--sidebar*` tokens — into `:root`, `.dark`, and the `@theme inline` mapping. Missing tokens silently break dependent primitives (transparent SelectContent, transparent Sidebar).

### Data layer

- All data access goes through the `DataProvider` interface via hooks.
- Never read from or write to localStorage directly in components.
- Static configs (emotions, domains, card types) are imported directly — they do not go through the provider.

### Supabase & Auth

- **Never `await` inside `onAuthStateChange` callbacks.** The Supabase client holds an internal lock during initialization. Awaiting any Supabase call (database, auth, storage) inside this callback creates a deadlock — the lock waits for the callback, the callback waits for the Supabase call, the call waits for the lock. Use fire-and-forget (`Promise.resolve(...).then(...)`) instead.
- Wrap any Supabase call that blocks rendering or user interaction with `withTimeout()` from `lib/utils/with-timeout.ts`. Use 10 s as the default timeout.
- Auth state is driven by `onAuthStateChange` (synchronous callback), not by `getUser()` (network call that can hang).

### Error visibility & logging

- Any async operation that can fail or hang **must** have a `console.warn` or `console.error` in its catch/error path. Silent failures (empty catch blocks, swallowed errors) are bugs — they make debugging impossible.
- Use `withTimeout()` on any async operation that blocks rendering. A timeout error with a label (`"profile fetch"`, `"session check"`) is infinitely more useful than a silent hang.
- In development, add watchdog timers for critical loading states (see the auth watchdog in `useSupabaseAuth` as a pattern). If a loading state persists beyond a reasonable threshold (3–5 s), log a warning with diagnostic hints.
- Never guard `console.warn`/`console.error` behind `NODE_ENV === "development"` for operations that can fail in production. Dev-only guards on error logs hide production issues.

### Testing mindset

- Even without tests in V1, write code as if tests will be added: pure functions, clear inputs/outputs, no hidden side effects.
- Keep business logic out of components — put it in hooks or utility functions.

### Sanity & Consistency

- Follow the established patterns in the codebase. If you need to introduce a new pattern, discuss it first.
- Keep the code clean and readable. Favor clarity over cleverness.
- Use consistent formatting and naming conventions.
- Comment non-obvious code with clear explanations of intent and reasoning.
- Always consider edge cases and error handling, even if it's just logging for now.
- Always check if the build and linting pass before ending your work.

## Git Conventions

### Commits

- One logical change per commit.
- Conventional commits format, lowercase, no period.
  Format: `type(scope): description`
  Types: `feat`, `fix`, `chore`, `docs`, `test`, `quality`
  Scope is optional, matches label scopes: `core`, `ui`, `db`, `api`, `auth`, `facility`
  Examples: `feat(ui): add emotion picker grid component`, `fix(db): correct seed data validation`

### Branches

- Format: `type/issueNumber-description`
- Examples: `feat/12-path-timeline-view`, `fix/42-emotion-picker-crash`
- The branch MUST be created with the correct naming before any commit is made.

### Labels

- Apply one species label: `feat`, `fix`, `bug`, `chore`, `docs`, `test`, `quality`
- Apply one or more scope labels: `core`, `ui`, `db`, `api`, `auth`, `facility`

### Issue titles

- Format: `[Type] Description` (e.g. `[Feat] Path timeline view`, `[Bug] Emotion picker crash`)

## PR Workflow Checklist

When creating a PR, you MUST follow this checklist:

1. **Branch name**: verify it matches `type/issueNumber-description` before pushing.
2. **PR title**: use conventional commits format `type(scope): description`.
3. **PR body**: start with `Resolves #N` (or `Closes #N`), list key files changed, include implementation notes.
4. **Labels and milestone**:
   - If the PR resolves an issue, propose inheriting the same labels and milestone from that issue and ask the user to confirm (except if the issue is labelled with `bug`, the PR will be labelled with `fix`).
   - If the PR does not resolve an issue, ask the user which species label, scope label(s), and milestone to apply.
   - Never create a PR without labels and a milestone (except if user confirmed there's no milestone).
5. **Build and lint**: always run `npm run build` and `npm run lint` and confirm they pass before opening the PR.

# Product Architecture Map (Arkaik)

Pebbles' product architecture is described in an Arkaik ProjectBundle JSON file at `docs/arkaik/bundle.json`. This map is the source of truth for all screens, flows, data models, and API endpoints in the product.

**Whenever your work changes the product architecture** — adding a screen, creating a route, defining a model, wiring an endpoint, removing a feature, or changing a feature's status — **update the map as part of the same change.**

Don't wait to be asked. Use the `arkaik` skill (`.claude/skills/arkaik/`) which explains the schema, the surgical update patterns, and includes a validation script to run before saving.

Keep changes surgical: only touch the nodes and edges affected by your work. Never regenerate the full map unless bootstrapping from scratch.