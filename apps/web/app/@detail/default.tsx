/**
 * The detail slot, closed (#1384, two-area spec D2).
 *
 * A parallel route needs a `default` for every route that does not fill it:
 * this is what a hard load of a tab, or of any page without a detail,
 * renders here. Nothing, so the right area shows the Path's line (from
 * 1024 px) or no overlay (below).
 *
 * It is not enough on its own, and `page.tsx` with `[...rest]/page.tsx` are the other half:
 * a `default` only covers routes the router cannot resolve (a hard load). On a
 * soft navigation Next keeps the slot's previous page instead, so going from
 * a detail back to its tab would leave the detail open over it. The catch-all
 * matches every such URL and renders nothing, so the slot closes. Ariko's
 * screen library hit exactly this (its `@sheet/screens/page.tsx`).
 */
export default function NoDetail() {
  return null
}
