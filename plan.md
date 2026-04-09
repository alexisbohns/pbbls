# Plan: Issue #177 — Markdown Rendering Infrastructure

## Summary

Issue #177 requests a reusable `MarkdownRenderer` component in `components/ui/` that renders markdown strings using `react-markdown` + `remark-gfm`, styled exclusively with Tailwind utilities and supporting light/dark themes.

## Steps

### 1. Install dependencies

```bash
npm install react-markdown remark-gfm
```

Both packages are new to the project — neither is currently in `package.json`.

### 2. Create `components/ui/MarkdownRenderer.tsx`

**Props interface** (inline, co-located with the component per project conventions):

```ts
type MarkdownRendererProps = {
  content: string
  className?: string
}
```

**Implementation approach:**

- Use `react-markdown` with the `remark-gfm` plugin (for tables, strikethrough, task lists, autolinks).
- Map markdown elements to styled HTML via the `components` prop:

| Element       | Tailwind classes                                                                 |
|---------------|----------------------------------------------------------------------------------|
| `h1`          | `font-heading text-xl font-semibold leading-snug mt-6 mb-3`                     |
| `h2`          | `font-heading text-lg font-semibold leading-snug mt-5 mb-2`                     |
| `h3`          | `font-heading text-base font-semibold leading-snug mt-4 mb-2`                   |
| `h4`          | `font-heading text-sm font-semibold leading-snug mt-3 mb-1`                     |
| `h5`          | `font-heading text-xs font-semibold leading-snug mt-3 mb-1`                     |
| `h6`          | `font-heading text-xs font-medium leading-snug mt-3 mb-1 text-muted-foreground` |
| `p`           | `mb-3 leading-relaxed`                                                           |
| `a`           | `text-primary underline underline-offset-4 hover:text-primary/80`               |
| `ul`          | `mb-3 ml-6 list-disc`                                                            |
| `ol`          | `mb-3 ml-6 list-decimal`                                                         |
| `li`          | `mb-1`                                                                           |
| `blockquote`  | `mb-3 border-l-2 border-border pl-4 text-muted-foreground italic`               |
| `code` (inline)| `rounded bg-muted px-1.5 py-0.5 font-mono text-sm`                             |
| `pre`         | `mb-3 overflow-x-auto rounded-lg bg-muted p-4`                                  |
| `code` (block, inside `pre`) | `font-mono text-sm`                                            |
| `strong`      | `font-semibold`                                                                  |
| `em`          | `italic`                                                                         |
| `hr`          | `my-6 border-border`                                                             |
| `table`       | `mb-3 w-full border-collapse text-sm`                                            |
| `th`          | `border border-border bg-muted px-3 py-2 text-left font-semibold`               |
| `td`          | `border border-border px-3 py-2`                                                 |

- Wrap the output in a `<div>` with the `className` prop merged via `cn()`.
- Distinguish inline `code` from block `code` by checking if the parent is a `pre` element (using the `node` argument in the component mapper — specifically checking if `node.parent?.tagName === 'pre'` or via `inline` detection).

**Export**: Named export `MarkdownRenderer`.

**File follows project patterns:**
- PascalCase filename
- Named export (not default)
- Inline props type
- `cn()` for class merging
- Tailwind-only styling
- No `any` types

### 3. Arkaik bundle update

This change does **not** require an Arkaik bundle update. The `MarkdownRenderer` is a UI primitive (like `Badge` or `Card`) — it's not a screen, route, data model, or API endpoint. It doesn't change the product architecture graph.

### 4. Build & lint verification

```bash
npm run build
npm run lint
```

Verify both pass before committing.

### 5. Commit & push

Single commit:
```
feat(ui): add markdown renderer component
```

Push to branch `claude/plan-issue-177-E097W`.

## Files changed

| File | Action |
|------|--------|
| `package.json` | Modified (new dependencies) |
| `package-lock.json` | Modified (lockfile update) |
| `components/ui/MarkdownRenderer.tsx` | **Created** |

## Risks & notes

- **No `@tailwindcss/typography`**: The issue explicitly disallows it. All prose styles are hand-mapped via `react-markdown`'s `components` prop, which gives full control per element.
- **Theme support**: All colors use CSS variable-backed Tailwind classes (`text-primary`, `bg-muted`, `border-border`, etc.), so light/dark mode works automatically via `next-themes`.
- **Semantic HTML**: `react-markdown` outputs semantic HTML by default (headings, lists, blockquotes). The component mappings preserve this.
- **GFM**: `remark-gfm` adds tables, strikethrough, task lists, and autolinks — covering GitHub-flavored markdown.
