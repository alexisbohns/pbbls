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
- UI primitives live in `components/ui/` (shadcn/ui). Do not duplicate them.
- Domain components live in feature folders: `components/path/`, `components/pebble/`, `components/record/`, etc.
- Hooks encapsulate data access. Components never call the provider directly.

### File & Naming Conventions

- Component files: PascalCase (`PebbleCard.tsx`).
- Hooks: camelCase prefixed with `use` (`usePebbles.ts`).
- Config and utility files: kebab-case (`card-types.ts`).
- One exported component per file. Co-locate sub-components only if they are exclusively used by the parent.

### TypeScript

- Strict mode. No `any`. No type assertions unless absolutely necessary.
- Define types in `lib/types.ts` for domain entities.
- Props types are defined inline or co-located with the component, not in `types.ts`.

### Accessibility

- All interactive elements must be keyboard-navigable.
- Use semantic HTML elements like `button` for interaction, `nav` for navigation, `main` for main content, `section` for sections, `h1`–`h6` for headings, `ul`/`ol` for lists even if it's a grid of cards, `article` for standalone content (ie. a pebble card), abbriviations like `abbr` for emotion icons, etc.
- Images and icons must have meaningful `alt` text or `aria-label`.
- Use `aria-live` regions for dynamic content updates.
- Color is never the sole indicator — always pair with text or icons.
- Follow WCAG 2.1 AA as the baseline.

### Styling

- Use Tailwind CSS utility classes. Avoid custom CSS unless necessary.
- Use shadcn/ui components as the base. Extend with CVA variants if needed.
- Support light and dark themes via CSS variables and `next-themes`.

### Data layer

- All data access goes through the `DataProvider` interface via hooks.
- Never read from or write to localStorage directly in components.
- Static configs (emotions, domains, card types) are imported directly — they do not go through the provider.

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
   - If the PR resolves an issue, propose inheriting the same labels and milestone from that issue and ask the user to confirm.
   - If the PR does not resolve an issue, ask the user which species label, scope label(s), and milestone to apply.
   - Never create a PR without labels and a milestone.
5. **Build and lint**: always run `npm run build` and `npm run lint` and confirm they pass before opening the PR.