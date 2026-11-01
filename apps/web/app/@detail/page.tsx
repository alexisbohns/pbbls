/**
 * The detail slot at the landing (#1384, two-area spec D2): nothing, for the
 * same reason as `[...rest]/page.tsx` (a soft navigation to "/" keeps a slot's
 * previous page when nothing matches). The catch-all cannot be optional here:
 * Next refuses `[[...rest]]` beside the root page.
 */
export default function NoDetailAtRoot() {
  return null
}
