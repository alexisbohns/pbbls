# A11Y — Accessibility & Inclusion

> Generated from [`library/framework.json`](../library/framework.json) v0.1.0 — do not edit by hand.

WCAG 2.2 AA, VoiceOver/TalkBack, Dynamic Type and font scaling, theming, motion, localization (EN/FR), inclusive content.

---

## A11Y-01 · Keyboard operability and accessible semantics

**Can every interactive flow on the web surfaces be completed with a keyboard alone, through elements that expose a correct accessible name, role, and value, with a visible and unobscured focus indicator at every step?**

`wcag-keyboard-semantics` · applies to: `web` `admin` · default impact **4/5** · weight **3/3**

All functionality reachable by pointer is also reachable and operable by keyboard, with no traps and a logical tab order. Interactive elements are native controls or implement the full WAI-ARIA pattern (role, states, keyboard handlers, focus management for overlays and dialogs). Focus is always visible, never suppressed, and never fully obscured by sticky or floating UI. Decorative elements are hidden from the accessibility tree; informative ones carry accessible names.

*Why it matters:* Screen reader and keyboard-only users are fully excluded when custom widgets (pickers, sliders, drag surfaces) lack keyboard paths, and consumer products serving EU users fall under the European Accessibility Act, which makes this a legal exposure rather than a polish item.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | Interactive elements are divs or spans with onClick handlers and no role, tabIndex, or key handlers; focus outlines are globally removed in the stylesheet; dialogs and sheets do not trap or restore focus; no accessibility lint rule is configured. |
| **1 · Ad-hoc** | Native buttons, links, and inputs are used where a component library provides them, so parts of the UI happen to be operable, but custom widgets (sliders, pickers, canvases, drag surfaces) have no keyboard path, tab order is accidental, and focus styling is inconsistent across components. |
| **2 · Defined** | A deliberate convention is visible (accessible primitives such as Radix or shadcn for dialogs and menus, aria-* attributes on some custom widgets), the primary flows are keyboard-completable, but at least one custom widget lacks a keyboard equivalent or focus management, and nothing checks for regressions. |
| **3 · Managed** | Every flow is keyboard-completable; custom widgets follow the corresponding ARIA APG pattern including arrow-key semantics and aria-valuenow for adjustable controls; overlays trap and restore focus; an accessibility lint (eslint-plugin-jsx-a11y or equivalent) runs at error severity; a manual keyboard and screen reader pass is recorded for the main flows. |
| **4 · Verified** | CI enforces it: axe-core (or equivalent) runs against the key routes and fails the build on violations, the accessibility lint is a blocking gate, and at least the primary creation flow has an automated keyboard-only end-to-end test. |

### Audit checklist

- [ ] Open each Next.js app's eslint config (e.g. apps/web/eslint.config.mjs, apps/admin/eslint.config.mjs) and check whether eslint-plugin-jsx-a11y (or next/core-web-vitals which bundles part of it) is enabled and at what severity.
- [ ] Grep components for click handlers on non-interactive elements: rg -n "<(div|span)[^>]*onClick" apps/web/components apps/admin, then verify each hit has role, tabIndex and onKeyDown, or is wrapped in a real button.
- [ ] Grep stylesheets for suppressed focus: rg -n "outline: ?none|outline:none" apps/*/app apps/*/components, and confirm every hit is paired with a :focus-visible replacement style.
- [ ] Trace the primary creation flow (the multi-step record flow) end to end in code: for each step's controls (intensity/valence sliders, pickers, photo attach) confirm a native element or an APG-complete ARIA pattern (role=slider with aria-valuenow/min/max and arrow-key handlers).
- [ ] Inspect dialogs, sheets, and popovers: those built on library primitives (Radix/shadcn) can be assumed focus-managed; list any hand-rolled overlay (fixed-position div toggled by state) and check for focus trap and focus restore.
- [ ] Run axe against the running app's key routes (e.g. npx playwright with @axe-core/playwright, or a browser axe pass) and record violations for landmark structure, name/role/value, and tab order.

### Monitoring signals

- rg -n "outline: ?none" apps/web apps/admin returns no hit that lacks an adjacent :focus-visible rule
- eslint-plugin-jsx-a11y present in both web apps' eslint configs with rules at error severity
- A CI job exists that runs axe-core (or Lighthouse accessibility with a score gate) against key routes
- rg -n "<div[^>]*onClick" --glob '*.tsx' returns zero hits without role= on the same element

### References

- [WCAG 2.2, SC 2.1.1 Keyboard, SC 2.1.2 No Keyboard Trap — SC 2.1.1, SC 2.1.2](https://www.w3.org/TR/WCAG22/)
- [WCAG 2.2, SC 2.4.7 Focus Visible and SC 2.4.11 Focus Not Obscured (Minimum) — SC 2.4.7, SC 2.4.11](https://www.w3.org/TR/WCAG22/)
- [WCAG 2.2, SC 4.1.2 Name, Role, Value — SC 4.1.2](https://www.w3.org/TR/WCAG22/)
- [WAI-ARIA Authoring Practices Guide (APG) widget patterns — Patterns](https://www.w3.org/WAI/ARIA/apg/)
- EN 301 549, Accessibility requirements for ICT products and services — Clause 9 (Web)
- [Directive (EU) 2019/882 (European Accessibility Act) — Annex I](https://eur-lex.europa.eu/eli/dir/2019/882/oj)

### Typical remediation

Replace clickable non-interactive elements with native buttons or APG-complete widgets, restore focus-visible styling, add focus management to hand-rolled overlays, then lock it in with jsx-a11y at error severity and an axe CI pass over the key routes.

*Issue skeleton:* [`templates/a11y-01.md`](../templates/a11y-01.md)

---

## A11Y-02 · Contrast, reflow, and text resize

**Does all text meet WCAG 2.2 AA contrast (4.5:1, 3:1 for large text and UI components), does the layout survive 200 percent text zoom and 320 CSS pixel reflow without loss of content or function, and is color never the sole carrier of meaning?**

`wcag-visual` · applies to: `web` `admin` · default impact **3/5** · weight **2/3**

Every text and essential non-text element meets AA contrast ratios in every state (default, hover, disabled, placeholder, on-image). Content reflows to a single column at 320 CSS pixels and remains usable at 200 percent zoom without two-dimensional scrolling except where the content itself requires it (tables, charts). Information encoded in color (valence, status, chart series) is also conveyed by text, shape, icon, or position.

*Why it matters:* Low-vision users are the largest accessibility population, and contrast or zoom failures are silent: the product looks fine to the team while being unreadable to them. In data-heavy back-office views, color-only encodings make analytics wrong, not just ugly, for color-blind operators.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | Colors are picked ad hoc per component with raw hex values; body or secondary text sits below 4.5:1; layouts use fixed pixel widths that clip or overlap at 320 px; state and category are shown by color alone. |
| **1 · Ad-hoc** | The base palette happens to pass for primary text, but muted, placeholder, disabled, and on-tint text fail; nothing in the code or docs shows zoom or reflow was ever considered; charts distinguish series by hue only. |
| **2 · Defined** | A token system encodes intended pairs (foreground on background, muted-foreground on muted) and key pages reflow, but at least one token pair or component state measurably fails AA and there is no measurement in place. |
| **3 · Managed** | All token pairs are verified at 4.5:1 (3:1 for large text and UI components), key routes are checked at 320 px and 200 percent zoom, and every color encoding carries a redundant channel (label, icon, pattern); results of the check are recorded. |
| **4 · Verified** | Contrast is computed mechanically (a script over the token file, or axe color-contrast checks in CI) and viewport tests cover 320 px reflow; a failing pair or overflow fails the build. |

### Audit checklist

- [ ] Open the design token source (e.g. apps/web/app/globals.css with Tailwind v4 @theme blocks) and extract every foreground/background pair; compute ratios with a contrast script or a tool, covering muted, placeholder, destructive, and sidebar variants in both themes.
- [ ] Grep for raw color literals bypassing tokens: rg -n "#[0-9a-fA-F]{3,8}\b|rgb\(|hsl\(" apps/web/components apps/admin --glob '*.tsx', and check each hit's contrast in context (text over photos and tinted cards especially).
- [ ] Load key routes at a 320 px wide viewport and at 200 percent browser zoom; note any horizontal scrolling, clipped controls, or overlapping text (fixed-width flex/grid values in px are the usual culprits, grep for w-\[ and min-w-\[ arbitrary values).
- [ ] Inspect charts and status indicators in the analytics/back-office surface: confirm series and states are distinguishable without color (direct labels, icons, patterns, or position).
- [ ] Check text-over-image components (photo cards, media overlays) for a scrim or guaranteed-contrast treatment rather than raw text on user-supplied photos.

### Monitoring signals

- A token-contrast script exists in the repo and runs in CI (fails on a pair below 4.5:1 / 3:1)
- axe color-contrast rule enabled in the CI accessibility pass with zero violations on key routes
- rg -n "#[0-9a-fA-F]{6}" --glob '*.tsx' apps/web/components returns zero hits (all color goes through tokens)
- A viewport test or screenshot test at 320 px width exists for the main routes

### References

- [WCAG 2.2, SC 1.4.3 Contrast (Minimum) — SC 1.4.3](https://www.w3.org/TR/WCAG22/)
- [WCAG 2.2, SC 1.4.4 Resize Text and SC 1.4.10 Reflow — SC 1.4.4, SC 1.4.10](https://www.w3.org/TR/WCAG22/)
- [WCAG 2.2, SC 1.4.1 Use of Color and SC 1.4.11 Non-text Contrast — SC 1.4.1, SC 1.4.11](https://www.w3.org/TR/WCAG22/)
- EN 301 549, Accessibility requirements for ICT products and services — Clause 9 (Web)

### Typical remediation

Route all color through the token system, fix failing pairs at the token level so every consumer inherits the fix, add scrims for text over user imagery, replace fixed pixel widths with fluid constraints, and add a token-contrast script plus a 320 px viewport check to CI.

*Issue skeleton:* [`templates/a11y-02.md`](../templates/a11y-02.md)

---

## A11Y-03 · VoiceOver and TalkBack support

**Is every interactive and informative element on the mobile surfaces exposed to VoiceOver and TalkBack with a meaningful localized label, a correct role or trait, sensible grouping and order, and do custom gesture-driven controls offer accessible alternatives?**

`mobile-screen-readers` · applies to: `ios` `android` · default impact **4/5** · weight **3/3**

Every control announces what it is and does, in the user's language, using platform semantics (SwiftUI accessibility modifiers, Compose semantics). Related content is merged into single focusable units so traversal is efficient; decorative images are hidden. Custom drawn or gesture-driven controls (canvases, sliders, drag pickers) expose value semantics and adjustable or custom actions so they can be operated without the gesture. Since the two mobile surfaces mirror each other by rule, an accessibility affordance added on one is added on the other.

*Why it matters:* Mobile is where screen reader users most often live, and custom-rendered emotional-input controls (canvases, drag pickers) are precisely the elements platform frameworks cannot make accessible automatically; missing them locks blind users out of the core loop of the product.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | No accessibility modifiers or semantics blocks anywhere; custom drawn controls are invisible or announce nothing meaningful; icon-only buttons are unlabeled; decorative images are announced by filename. |
| **1 · Ad-hoc** | Platform defaults carry text-based controls, but icon buttons and images lack labels or carry hardcoded English ones; traversal order is accidental; at least one core custom control is inoperable with the screen reader. |
| **2 · Defined** | Main flows carry labels by visible convention (accessibilityLabel / contentDescription on most controls), but custom controls expose a label without value or adjustable actions, grouping is inconsistent, and some labels bypass the localization mechanism. |
| **3 · Managed** | Every interactive and informative element is labeled from localized string resources; custom controls expose role, current value, and adjustable or custom actions; related content is merged for traversal; a recorded manual VoiceOver and TalkBack pass covers the primary flows on both platforms, and the two platforms expose equivalent semantics. |
| **4 · Verified** | Automated semantics assertions run in CI: iOS accessibility audits or snapshot tests asserting labels and traits, Compose UI tests asserting semantics properties on key screens; a new unlabeled control fails a test. |

### Audit checklist

- [ ] iOS: rg -n "accessibilityLabel|accessibilityElement|accessibilityHidden|accessibilityAdjustableAction|accessibilityValue" apps/ios --glob '*.swift' and map the hits against the feature folders; a feature folder with heavy custom drawing (canvas, valence/intensity pickers, week path renderers) and zero hits is a finding.
- [ ] iOS: check that accessibilityLabel arguments are localized keys or String(localized:), not English literals; rg -n 'accessibilityLabel\("' apps/ios --glob '*.swift' surfaces hardcoded ones.
- [ ] Android: rg -n "contentDescription|Modifier.semantics|clearAndSetSemantics|mergeDescendants|progressSemantics|stateDescription" apps/android --glob '*.kt'; verify informative images do not pass contentDescription = null and icon-only IconButtons carry a resource-backed description.
- [ ] For each custom gesture-driven control (drag-to-set intensity, canvas drawing, swipe pagers), confirm an adjustable action (iOS accessibilityAdjustableAction, Compose semantics setProgress or custom actions) or a documented equivalent path exists on BOTH platforms.
- [ ] Diff the two platforms feature by feature (the repo rule is a 1:1 mirror): list controls that have semantics on one platform and not on the other.
- [ ] Run the platform inspector once per primary flow if a simulator/emulator is available (Xcode Accessibility Inspector audit, TalkBack with the record flow) and note unreadable or trap steps.

### Monitoring signals

- rg -c "accessibilityLabel" apps/ios and rg -c "contentDescription|semantics" apps/android both nonzero in every feature folder containing custom controls
- rg -n 'accessibilityLabel\("[A-Za-z]' apps/ios returns zero hits (no hardcoded-literal labels)
- A CI test target asserts semantics/labels on at least the primary flow screens for both platforms
- rg -n "contentDescription = null" apps/android hits only files whose images are decorative

### References

- [Apple Human Interface Guidelines, Accessibility — VoiceOver](https://developer.apple.com/design/human-interface-guidelines/accessibility)
- [Android Developers, Accessibility in Jetpack Compose — Semantics](https://developer.android.com/develop/ui/compose/accessibility)
- [Android Developers, Principles for improving app accessibility — Label elements](https://developer.android.com/guide/topics/ui/accessibility/principles)
- EN 301 549, Accessibility requirements for ICT products and services — Clause 11 (Software)
- [WCAG 2.2, SC 4.1.2 Name, Role, Value — SC 4.1.2](https://www.w3.org/TR/WCAG22/)

### Typical remediation

Label icon controls from string resources, hide decorative imagery, add value and adjustable-action semantics to each custom control, merge grouped content, and mirror every fix on the sibling platform; then pin the state with semantics assertions in each platform's test suite.

*Issue skeleton:* [`templates/a11y-03.md`](../templates/a11y-03.md)

---

## A11Y-04 · Dynamic Type, font scaling, touch targets

**Does every text element honor the user's system font scale (Dynamic Type on iOS, fontScale on Android) without truncation or clipped layouts, and does every touch target meet the platform minimum (44x44 pt on iOS, 48x48 dp on Android)?**

`mobile-scaling-targets` · applies to: `ios` `android` · default impact **3/5** · weight **2/3**

Text uses scalable platform text styles; custom fonts are registered relative to a text style (UIFontMetrics or relativeTo on iOS, sp units on Android) so they scale with the user's setting. Layouts adapt at the largest accessibility sizes instead of truncating meaning away. Every interactive element has a hit area at or above the platform minimum even when its visual glyph is smaller, typically via shared components or minimum-size modifiers.

*Why it matters:* Font scaling is the most used accessibility setting on mobile by a wide margin, and hand-drawn or decorative typography (common in expressive products) silently opts out of it when registered at fixed sizes; small touch targets exclude users with motor impairments and degrade everyone's one-handed use.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | Text sizes are fixed everywhere (.font(.system(size:)) without relativeTo, Android fontSize in dp or fixed TextUnit); custom fonts never scale; icon-sized tap areas (24 dp and under) are common with no minimum enforcement. |
| **1 · Ad-hoc** | Platform text styles are used in places so default text scales, but custom display fonts and numeric labels are fixed-size; lineLimit(1) plus minimumScaleFactor is used to fight scaling instead of adapting layout; touch minimums are met only where the component library imposes them. |
| **2 · Defined** | A deliberate typography layer exists (font helpers with relativeTo, sp everywhere in new code) and a target-size convention is stated, but legacy fixed sizes remain, the largest accessibility sizes clip on known screens, and nothing verifies hit areas. |
| **3 · Managed** | All text scales including custom fonts; key screens are verified at the largest accessibility text sizes on both platforms; every target meets 44 pt / 48 dp via shared components or explicit minimums, with documented exceptions only where the platform allows (inline text links); both platforms behave equivalently. |
| **4 · Verified** | Enforced by automation: a lint or grep gate rejects dp-based font sizes and fixed-size text APIs, and screenshot or UI tests run at maximum font scale in CI so clipping and undersized targets fail the build. |

### Audit checklist

- [ ] iOS: rg -n "\.font\(.system\(size:|Font.custom\(" apps/ios --glob '*.swift'; every Font.custom hit must carry relativeTo: (or route through a helper that does); list fixed-size hits, especially for the bundled custom/handwritten fonts.
- [ ] iOS: rg -n "minimumScaleFactor|lineLimit\(1\)" apps/ios --glob '*.swift' and judge whether each is adapting layout or defeating Dynamic Type on meaningful text.
- [ ] Android: rg -n "fontSize *= *[0-9.]+\.dp" apps/android --glob '*.kt' (must be zero, text is .sp) and rg -n "\.sp" to confirm the convention; check any custom Typography definitions use sp.
- [ ] Android: rg -n "Modifier\.size\(|clickable" apps/android --glob '*.kt' and flag clickable elements sized under 48.dp without minimumInteractiveComponentSize or a padded hit area; confirm LocalMinimumInteractiveComponentSize is not zeroed out anywhere.
- [ ] iOS: inspect shared tappable atoms (pills, badges, close buttons) for a contentShape or frame guaranteeing 44x44 pt even when the glyph is smaller.
- [ ] If an emulator/simulator is available, set text size to maximum accessibility scale and walk the primary flow on both platforms, recording truncation, overlap, or lost controls.

### Monitoring signals

- rg -n "fontSize *= *[0-9.]+\.dp" apps/android returns nothing
- rg -n "Font.custom\(" apps/ios returns only hits with relativeTo: or a scaling helper
- Screenshot tests exist that render key screens at maximum font scale (both platforms) and run in CI
- rg -n "minimumInteractiveComponentSize|contentShape" hits in the shared component layer of both apps

### References

- [Apple Human Interface Guidelines, Typography — Dynamic Type sizes](https://developer.apple.com/design/human-interface-guidelines/typography)
- [Apple Human Interface Guidelines, Accessibility — Text display](https://developer.apple.com/design/human-interface-guidelines/accessibility)
- [Android Developers, Principles for improving app accessibility — Touch targets (48dp)](https://developer.android.com/guide/topics/ui/accessibility/principles)
- [WCAG 2.2, SC 2.5.8 Target Size (Minimum) — SC 2.5.8](https://www.w3.org/TR/WCAG22/)
- [WCAG 2.2, SC 1.4.4 Resize Text — SC 1.4.4](https://www.w3.org/TR/WCAG22/)

### Typical remediation

Route all typography through a scaling helper (relativeTo / sp), replace scale-defeating minimumScaleFactor usage with adaptive layouts, give small glyphs padded hit areas via shared components, and add max-font-scale screenshot tests plus a dp-font-size grep gate to CI.

*Issue skeleton:* [`templates/a11y-04.md`](../templates/a11y-04.md)

---

## A11Y-05 · Dark/light parity and high-contrast modes

**Are both color themes complete and AA-compliant, driven by design tokens defined for each theme with no raw color literals bypassing them, and does the UI remain usable under system high-contrast or forced-colors settings?**

`theming` · applies to: `web` `ios` `android` `admin` · default impact **2/5** · weight **1/3**

Every color flows through a token defined in both light and dark themes, so no component renders an unreadable pair or an unthemed patch in either mode. The app follows the system theme setting (with an in-app override if offered) rather than hardcoding one palette. Under forced-colors or increased-contrast system settings, controls, focus indicators, and boundaries remain distinguishable instead of disappearing with their backgrounds.

*Why it matters:* Users with light sensitivity or low vision depend on one specific theme or on system contrast settings; a component that hardcodes a hex value works in the theme the developer ran and silently breaks in the other, which is exactly the drift class token systems plus enforcement exist to stop.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | One hardcoded palette; the second theme is absent or visibly broken (white flashes, unthemed surfaces, unreadable text); color literals scattered through components; forced-colors never considered. |
| **1 · Ad-hoc** | The component library's defaults give partial dark support, but many components hardcode colors, so each theme has screens with failing pairs; system theme changes are only partially picked up. |
| **2 · Defined** | A two-theme token system exists (CSS custom properties with a dark variant, asset catalog Any/Dark color sets, Compose light and dark color schemes) and most components use it, but literal bypasses exist and neither parity nor forced-colors behavior is checked anywhere. |
| **3 · Managed** | All color goes through tokens defined per theme, contrast is verified in both themes, feature reviews cover both modes, and custom controls keep visible borders and focus indicators under forced-colors / increased contrast; the mobile platforms honor the system dark setting identically. |
| **4 · Verified** | Drift is caught mechanically: a lint or grep gate rejects raw color literals in UI code, and dual-theme screenshot tests (or a token audit script covering both theme blocks) run in CI. |

### Audit checklist

- [ ] Web/admin: open the token stylesheet (Tailwind v4 @theme plus the .dark variant block in globals.css) and diff the two theme blocks: every token defined in light must be redefined in dark; list tokens missing from either block.
- [ ] Web/admin: rg -n "#[0-9a-fA-F]{3,8}\b|rgb\(|hsl\(" apps/web/components apps/admin --glob '*.tsx' to find literal bypasses; also rg -n "bg-\[|text-\[" for Tailwind arbitrary color values.
- [ ] iOS: list color sets in Assets.xcassets and confirm Any/Dark appearances exist; rg -n "Color\(red:|Color\(hex|UIColor\(" apps/ios --glob '*.swift' for literals outside the theme layer.
- [ ] Android: confirm a darkColorScheme is defined and selected via isSystemInDarkTheme (rg -n "darkColorScheme|isSystemInDarkTheme" apps/android); rg -n "Color\(0x" apps/android --glob '*.kt' for literals outside the theme package.
- [ ] Web/admin: rg -n "forced-colors|forced-color-adjust" apps/web apps/admin; for custom controls that draw their own borders/fills (toggles, sliders, canvases), check they remain visible when backgrounds are forced (system color keywords or transparent borders that become visible).
- [ ] Trace the theme switch path once per web app (class strategy, e.g. .dark on html, and how it is set) to confirm system preference is respected on first paint without a flash of the wrong theme.

### Monitoring signals

- A script or test diffs the light and dark token blocks and fails on a token defined in only one
- rg -n "Color\(0x" apps/android --glob '*.kt' hits only the theme package; same for Color(red:) on iOS
- Dual-theme screenshot tests exist for key screens and run in CI
- rg -n "forced-colors" apps/web returns at least one deliberate handling block for custom controls

### References

- [WCAG 2.2, SC 1.4.3 Contrast (Minimum) and SC 1.4.11 Non-text Contrast — SC 1.4.3, SC 1.4.11](https://www.w3.org/TR/WCAG22/)
- [Apple Human Interface Guidelines, Dark Mode — Color](https://developer.apple.com/design/human-interface-guidelines/dark-mode)
- [MDN, forced-colors media query — @media (forced-colors)](https://developer.mozilla.org/en-US/docs/Web/CSS/@media/forced-colors)

### Typical remediation

Move literal colors into per-theme tokens, complete the missing theme definitions, verify pairs in both modes, add forced-colors handling to self-drawn controls, then gate with a literal-color lint and dual-theme screenshots in CI.

*Issue skeleton:* [`templates/a11y-05.md`](../templates/a11y-05.md)

---

## A11Y-06 · Reduced motion honored across all animation

**Is every non-essential animation, including vector animation runtimes (Rive/Lottie), parallax, springy transitions, and autoplaying loops, disabled or replaced with a static equivalent when the user's reduce-motion preference is set, and can any moving content lasting over five seconds be paused?**

`motion` · applies to: `web` `ios` `android` `admin` · default impact **3/5** · weight **2/3**

The platform's reduce-motion preference (prefers-reduced-motion, iOS Reduce Motion, Android animator scale settings) is read through one shared helper and gates every animation entry point, not just the ones a developer remembered. Animation-runtime assets (Rive, Lottie) either do not autoplay or render a static final frame under reduced motion. Long-running or looping movement can be paused, stopped, or hidden. Essential motion (a progress indicator conveying state) may remain but is minimized.

*Why it matters:* Vestibular disorders make parallax and large springy motion physically harmful, not merely annoying, and expressive products lean hardest on exactly that kind of signature motion; a preference honored in some components but not in the animation-heavy ones fails the users it exists for.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | No occurrence of the reduce-motion APIs anywhere in the surface's code; animations and animation-runtime assets autoplay unconditionally; loops cannot be paused. |
| **1 · Ad-hoc** | One or two components check the preference, discovered by grep, but there is no shared helper and the animation-heaviest surfaces (transitions, vector loops, celebratory effects) ignore it. |
| **2 · Defined** | A shared hook or environment read exists and covers the primary animation surfaces, but an inventory of animation entry points against reduced-motion gates shows uncovered ones (typically the animation-runtime wrappers or CSS keyframes), and long loops still lack a pause path. |
| **3 · Managed** | Every non-essential animation entry point routes through the shared gate, vector assets fall back to a static frame, loops over five seconds are pausable or auto-stop, and a recorded manual pass with the setting enabled covers the main flows on each platform. |
| **4 · Verified** | The gate is enforced: a lint or CI check verifies animation entry points import the reduced-motion helper (or tests run with the preference forced on and assert static rendering), so a new ungated animation fails the build. |

### Audit checklist

- [ ] Build the animation inventory per surface: rg -ln "animate|Animation|withAnimation|animateFloat|keyframes|transition|rive|lottie" for each app, then rg -ln "prefers-reduced-motion|useReducedMotion|accessibilityReduceMotion|isReduceMotionEnabled|ANIMATOR_DURATION_SCALE" and diff the two lists; files in the first list but not gated (directly or via a shared wrapper) are findings.
- [ ] Verify a single shared helper exists per surface (a useReducedMotion hook on web, an @Environment(\.accessibilityReduceMotion) or UIAccessibility read on iOS, a Settings.Global animator-scale read on Android) rather than per-component media queries; grep for duplicated ad hoc checks.
- [ ] Locate vector-animation wrappers (Rive/Lottie view components, rg -ln "\.riv|RiveView|rive-react") and confirm autoplay and loop settings are gated with a static fallback frame.
- [ ] Check CSS keyframe animations on web are wrapped in @media (prefers-reduced-motion: no-preference) or disabled under reduce (rg -n "@keyframes" apps/web and inspect surrounding media queries).
- [ ] Identify any moving content that runs over five seconds (ambient loops, background turbulence/wobble effects) and confirm a pause/stop control or auto-stop exists.

### Monitoring signals

- Diff of animation-entry-point files vs reduced-motion-gated files is empty (scriptable as a CI check)
- rg -n "autoplay" in vector-animation wrappers shows every autoplay behind the reduced-motion gate
- One shared reduced-motion helper per surface, imported by all animating components (grep for direct media-query or UIAccessibility reads outside it returns only the helper)
- A UI test runs with the reduce-motion preference forced on and asserts no animation starts

### References

- [WCAG 2.2, SC 2.2.2 Pause, Stop, Hide — SC 2.2.2](https://www.w3.org/TR/WCAG22/)
- [WCAG 2.2, SC 2.3.3 Animation from Interactions — SC 2.3.3](https://www.w3.org/TR/WCAG22/)
- [Apple Human Interface Guidelines, Motion — Reduce Motion](https://developer.apple.com/design/human-interface-guidelines/motion)
- [MDN, prefers-reduced-motion media query — @media (prefers-reduced-motion)](https://developer.mozilla.org/en-US/docs/Web/CSS/@media/prefers-reduced-motion)

### Typical remediation

Introduce one reduced-motion helper per surface, route every animation entry point (including vector-runtime wrappers and CSS keyframes) through it with static fallbacks, add pause affordances to long loops, and add a CI diff between animation entry points and gated files.

*Issue skeleton:* [`templates/a11y-06.md`](../templates/a11y-06.md)

---

## A11Y-07 · Localization completeness and locale-safe formatting

**Is every user-facing string, including accessibility labels, errors, empty states, notifications, and server-sourced catalog content, externalized and present in every supported locale, with dates, numbers, and plurals produced by locale-aware APIs?**

`i18n` · applies to: `web` `ios` `android` `supabase` · default impact **3/5** · weight **3/3**

No user-visible literal lives in component or view code; all copy flows through the surface's localization mechanism and every key exists in every supported locale, including the strings only assistive technology reads. Dates, times, numbers, ordinals, and plural forms come from locale-aware formatters (Intl / ECMA-402, platform formatters, plural resources), never string concatenation. Localized content that lives server-side (reference catalogs, notification and email templates) has the same per-locale completeness, and the document or view declares its language correctly so screen readers pick the right voice.

*Why it matters:* For a bilingual EU-first audience, a missing translation is a broken product for half the users, and untranslated accessibility labels break screen reader output twice over; locale drift between clients that share one database shows up as mixed-language screens that no single-surface test catches.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | Strings are hardcoded in one language throughout the UI code; the second locale is absent or a stub; dates and plurals are built by concatenation ('s' appended by hand); server-sourced content exists in one language. |
| **1 · Ad-hoc** | A resource mechanism exists (message JSON, xcstrings, values-fr) but coverage is partial: user-visible literals remain in code, locale files have drifted key sets, and formatting is a mix of locale-aware calls and manual strings. |
| **2 · Defined** | All new copy is externalized by visible convention and the locale files are near parity, but known gaps exist (accessibility labels, error toasts, or notification templates untranslated; catalog tables missing a locale column) and nothing detects drift. |
| **3 · Managed** | Full key parity across locales verified for every string class (UI copy, a11y labels, errors, empty states, push/email templates, server catalog rows), all formatting via Intl or platform formatters with plural resources, correct lang declaration per document; parity is part of review for every copy change. |
| **4 · Verified** | Drift is impossible to miss: CI diffs locale key sets and fails on a missing key (script for message JSON, MissingTranslation as error on Android, xcstrings state check on iOS), a lint flags user-visible literals in components, and a harness asserts per-locale completeness of server-sourced catalog content. |

### Audit checklist

- [ ] Web: diff message key sets, e.g. jq -r 'paths(scalars) | join(".")' apps/web/lib/i18n/messages/en.json | sort > /tmp/en.keys (same for fr.json) and diff; a nonempty diff is a finding. Then rg -n '>[A-Z][a-z].*</' apps/web/components --glob '*.tsx' to sample user-visible literals outside the t() path.
- [ ] iOS: parse Localizable.xcstrings for entries whose secondary-locale state is missing or new (rg -n '"state" : "(new|needs_review)"' or a jq pass over the file); rg -n 'Text\("[A-Za-z]' apps/ios --glob '*.swift' for literals bypassing the catalog.
- [ ] Android: compare string names between res/values/strings.xml and res/values-fr/strings.xml (grep -o 'name="[^"]*"' each, sort, diff); check lint config treats MissingTranslation as an error; rg -n 'text = "' apps/android --glob '*.kt' for literals.
- [ ] Formatting: rg -n "toLocaleDateString|Intl\." apps/web plus rg -n "DateTimeFormatter|NumberFormat|getQuantityString" apps/android and rg -n "formatted\(|FormatStyle|String\(localized" apps/ios; then hunt the antipatterns: string concatenation around dates/counts (rg -n '\+ " " \+|\$\{.*count.*\}s\b').
- [ ] Supabase: open the seed/reference migrations for catalog tables (emotions, domains, achievements) and confirm every supported locale has a label column or i18n JSON per row with no nulls; check edge functions that compose user-facing text (emails, push payloads) select copy by the recipient's locale rather than embedding one language.
- [ ] Web: confirm the root layout sets the html lang attribute from the active locale (not hardcoded to one language) and that any deliberately untranslated fragment carries lang= markup.

### Monitoring signals

- A CI step diffs locale key sets per surface and fails on drift (message JSON diff script, Android MissingTranslation=error, xcstrings state check)
- rg -n 'Text\("[A-Za-z]{3,}' apps/ios returns zero user-visible literals (only keys/symbols)
- A harness or SQL check asserts no null locale column in reference catalog tables
- rg -n 'lang="en"' apps/web/app/layout.tsx returns nothing hardcoded (lang is computed from the locale)

### References

- [WCAG 2.2, SC 3.1.1 Language of Page and SC 3.1.2 Language of Parts — SC 3.1.1, SC 3.1.2](https://www.w3.org/TR/WCAG22/)
- [ECMA-402, ECMAScript Internationalization API Specification — Intl](https://tc39.es/ecma402/)
- [Apple Developer Documentation, Localization — String catalogs](https://developer.apple.com/documentation/xcode/localization)
- [Android Developers, Localize your app — Resource files](https://developer.android.com/guide/topics/resources/localization)

### Typical remediation

Externalize remaining literals into the locale mechanism, backfill the missing keys with real adaptations, replace concatenation with Intl/platform formatters and plural resources, add locale columns or i18n JSON for server catalogs, and wire key-set diffs into CI per surface.

*Issue skeleton:* [`templates/a11y-07.md`](../templates/a11y-07.md)

---

## A11Y-08 · Inclusive language and emotional vocabulary

**Is user-facing copy in every locale free of gendered defaults, ableist idioms, and stigmatizing framing, and does the product's emotion and relationship vocabulary let users describe themselves and others in neutral, non-judgmental, self-determined terms?**

`inclusive-content` · applies to: `web` `ios` `android` `supabase` · default impact **3/5** · weight **2/3**

Copy addresses the user without assuming gender; in grammatically gendered locales it uses epicene constructions or inclusive forms rather than defaulting to the masculine. Identity inputs (gender, pronouns, relationship types) are optional, offer self-describe options, and never force a binary. The controlled vocabulary for emotional states and personal relationships uses neutral, non-clinical, non-moralizing labels: negative states are presented as valid experiences, not failures, and no term pathologizes the user. Ableist or violent idioms are absent from all locales, including error and empty-state copy.

*Why it matters:* In a product where users log emotional states and name real people in their lives, copy that shames negative feelings, misgenders the user, or forces categories does concrete harm to exactly the vulnerable moments the product exists for, and the harm is duplicated per locale when translations default to gendered forms.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | Copy defaults to masculine forms throughout the gendered locale; identity fields are required binaries; emotional-state labels carry judgment (weakness, failure framing) or clinical diagnoses; ableist intensifiers appear in the strings files. |
| **1 · Ad-hoc** | Some neutral phrasing exists where the sentence happened to allow it, but there is no stated guideline, translations are literal with gendered defaults, and the vocabulary tables were never reviewed for framing. |
| **2 · Defined** | A written copy or tone guideline exists covering inclusivity, most strings conform, but grep still finds gendered defaults or flagged idioms in at least one locale, and the server-side vocabulary catalog has not been through a documented review. |
| **3 · Managed** | Every locale's strings reviewed against the guideline; the emotion/relationship catalog reviewed for neutral, non-clinical framing with the review recorded; identity inputs optional with self-describe; new copy goes through the guideline at review time on every surface. |
| **4 · Verified** | A per-locale deny-list lint (ableist idioms, gendered default forms) runs in CI over all string resources, and additions to the vocabulary catalog require a recorded review step before the migration lands. |

### Audit checklist

- [ ] Grep the gendered locale's resources for masculine-default participles and adjectives addressing the user: rg -n "\b(prêt|sûr|connecté|inscrit|heureux|content|certain)\b" apps/web/lib/i18n/messages/fr.json apps/android/app/src/main/res/values-fr/strings.xml and the iOS string catalog; judge each hit for an epicene rewrite.
- [ ] Grep all locales for ableist or violent idioms used as intensifiers: rg -in "\b(crazy|insane|dumb|lame|blind to|fou|dingue|débile)\b" over every string resource file, and review hits in context.
- [ ] Open the seed/reference migrations for the emotion and relationship vocabularies and read every label in every locale: flag clinical terms, moralizing framing (labels implying a state is a fault), and translations that shifted register between locales.
- [ ] Trace identity inputs in the profile and person-tagging flows on each client: confirm gender/pronoun fields (if any) are optional with a self-describe or unspecified option, and no flow blocks on a binary choice.
- [ ] Check celebration/streak and empty-state copy in all locales for shame framing around missed days or negative-valence entries (guilt-based prompts), and confirm negative states get the same neutral treatment as positive ones.
- [ ] Look for a written copy guideline in the repo (docs/ or the design system docs) that covers inclusive language per locale; its absence caps the score at level 1.

### Monitoring signals

- A deny-list lint over string resources exists per locale and runs in CI with zero hits
- rg -in "\b(crazy|insane|fou de|dingue)\b" over string resources returns nothing
- A documented review entry exists for the current vocabulary catalog (decision log or spec)
- Identity fields in the schema are nullable and free-text-capable rather than constrained enums (check the migrations for gender/pronoun columns)

### References

- [Apple Human Interface Guidelines, Inclusion — Gender identity and Avoiding stereotypes](https://developer.apple.com/design/human-interface-guidelines/inclusion)
- [Google developer documentation style guide, Write inclusive documentation — Ableist and gendered language](https://developers.google.com/style/inclusive-documentation)

### Typical remediation

Write a short per-locale inclusive-copy guideline, sweep existing strings and vocabulary catalogs against it (epicene rewrites, idiom replacements, neutral emotional framing), make identity inputs optional and self-describable, and add a per-locale deny-list check to CI so regressions surface at PR time.

*Issue skeleton:* [`templates/a11y-08.md`](../templates/a11y-08.md)
