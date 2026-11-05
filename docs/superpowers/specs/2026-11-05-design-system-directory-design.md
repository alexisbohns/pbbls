# Web: the design page becomes a design system directory — design

**Date:** 2026-11-05 · **Surface:** web (sandbox only) · **Builds on:** `2026-09-28-web-m3-design-page-design.md` (#986)

## Goal

Turn `/sandbox/design` into a directory of the whole web design system (colours, icons, components, views) where every part can be overviewed and inconsistencies pinpointed. Components and views are shown as **mobile and desktop screens**, at their real size scaled down to fit the page.

Real users see no change: everything lives under `/sandbox`, which is `noindex` and exempt from the auth and consent gates.

## Routes

| Route | Shows |
|---|---|
| `/sandbox/design` | Overview: one card per category, then the inconsistency checks |
| `/sandbox/design/colors` | App tokens, M3 roles and the bridge, emotion palettes, type, shape, stones, each followed by what the source writes off-system |
| `/sandbox/design/icons` | Every `@pbbls/icons` row and twin at the `PBBLS_ICON_SIZE` sizes, its migration per surface, and the Lucide icons still imported on web |
| `/sandbox/design/components` | Every specimen, one pair of screens (mobile, desktop) per group |
| `/sandbox/design/views` | Every view on every device; zoomed out, a wall of the whole app |
| `/sandbox/design/frame` | One view (`?view=`) or a set of specimens (`?specimens=`) alone, the iframe target and the "1:1" link |

The directory pages share `(directory)/layout.tsx`: the toolbar (tabs, Theme, World/Contrast, Mode, and on the canvas pages Screens and Zoom) and the `T`/`D` shortcuts. The theme stays in the URL; tab links carry it.

## Screens

- Devices: Mobile 390×844, Desktop 1440×900 (`lib/design/devices.ts`). Adding one there adds it everywhere.
- Each screen is an iframe of the device's viewport, scaled with `transform: scale()`. All screens in a row share one scale, so their proportions stay true. Zoom: **Fit** (both side by side in the column, never above 1:1), 25, 50, 75, 100 %.
- Views keep the device height and scroll inside; specimen groups grow to their content (the frame reports its height), except Overlays, which keep the device height so a dialog has room.
- Frames mount within one screen of the visible area and unmount three screens away.
- Theme changes go to live frames by `postMessage` (`lib/design/frame-protocol.ts`); a frame that loads later asks for the theme once it listens.

## Inconsistency checks

`lib/design/audit.ts` (pure, Vitest) scans `components/` and `app/` (the sandbox and tests excluded) with comments blanked:

| Check | What counts |
|---|---|
| Lucide icons | every name imported from `lucide-react`, matched to the manifest's `replaces.web` |
| Literal colours | hex (`#RGB[A]`, `#RRGGBB[AA]`) and `rgb/hsl/oklch/oklab/lab/lch()` not built on a `var()` |
| Default palette | Tailwind's own hues and black/white on colour utilities |
| Off-scale values | `utility-[value]` that is not a token, a colour or a variant, sorted into type, radius, spacing, size, other |
| Not tuned to M3 | registry entries still `bridged` |
| No specimen | component files per domain folder the page never draws on its own |

`lib/design/audit-source.ts` runs it from the prerendered pages, so the build computes it once and `next dev` recomputes on reload. It is a prompt to look, not a gate.

## Out of scope

- Fixing any finding. Each is a follow-up in the component's own PR.
- Tablet or other device sizes (one line in `devices.ts` when wanted).
- iOS and Android design catalogues.
