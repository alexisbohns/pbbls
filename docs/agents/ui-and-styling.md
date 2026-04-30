# UI & Styling Rules

Read this when building, modifying, or styling UI components.

## Atomic Design & Composition

- Build small, composable, single-responsibility components. If a component grows a second responsibility, split it.
- Avoid duplicating components — reuse and compose. A status badge with only an icon and another with icon + text should be the same component with a variant prop.
- UI primitives live in `<app>/components/ui/` (shadcn/ui), one set per app. Do not duplicate primitives within an app.
- Domain components live in feature folders: `apps/web/components/path/`, `apps/web/components/pebble/`, `apps/web/components/record/`, etc.
- Hooks encapsulate data access. Components never call the provider directly.
- Sidebar is customizable per page via the sidebar prop: `<PageLayout sidebar={<PathProfileCard />}>...</PageLayout>`

## shadcn-first

- Reach for shadcn first when introducing a layout or interaction pattern (sidebar, breadcrumb, dropdown menu, dialog, sheet, tooltip, command palette, etc.). Do not hand-roll a `<nav>` + `<ul>` when `Sidebar`/`SidebarMenu` exists, or stitch a `<form action="/signout">` button when `DropdownMenu` already does the right thing.
- Add primitives via `npx shadcn@latest add <name>` from inside the app folder — never hand-write a primitive that the registry ships.
- Don't surcharge shadcn primitives. If you find yourself appending more than a handful of utility classes, or rewriting the inner markup, you're probably reaching for the wrong primitive — pick a different one rather than fighting the one you have.

## base-nova quirks (NOT Radix)

This monorepo's shadcn style is `base-nova`, which sits on `@base-ui/react`, **not** Radix. Consequences:

- No `asChild` prop on `Button`, `SidebarMenuButton`, `DropdownMenuTrigger`, `BreadcrumbLink`, etc.
- Use `<Link className={buttonVariants(...)}>` for nav-as-button.
- Use `render={<Link href=…/>}` where the primitive accepts a `render` prop.
- Pair `<Label htmlFor=…>` with the input's `id` (including `<SelectTrigger id=…>`). Don't wrap a Select in a `<label>` — Base UI's hidden-input wiring doesn't auto-associate that way.

## Theming

- Tailwind utility classes only. Avoid custom CSS unless necessary.
- Extend with CVA variants if needed.
- Support light and dark themes via CSS variables. When scaffolding a new app's `globals.css`, copy the **complete** token set from `apps/web/app/globals.css` — including `--popover*` and the eight `--sidebar*` tokens — into `:root`, `.dark`, and the `@theme inline` mapping. Missing tokens silently break dependent primitives (transparent SelectContent, transparent Sidebar).

## Accessibility (WCAG 2.1 AA)

- All interactive elements keyboard-navigable.
- Use semantic HTML: `button` for interaction, `nav` for navigation, `main`, `section`, `h1`–`h6`, `ul`/`ol` even for grids of cards, `article` for standalone content (e.g. a pebble card), `abbr` for emotion icons, etc.
- Images and icons must have meaningful `alt` text or `aria-label`.
- Use `aria-live` regions for dynamic content updates.
- Color is never the sole indicator — pair with text or icons.
