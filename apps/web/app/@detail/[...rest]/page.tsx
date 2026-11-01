/**
 * The detail slot on every URL that is not a detail (#1384, two-area spec D2).
 *
 * DELETING THIS AS DEAD CODE REOPENS A BUG: on a soft navigation Next keeps a
 * slot's previous page when nothing in the slot matches the new URL, so
 * closing a detail (or tapping a tab) would leave it open over the page.
 * Matching every URL here, with nothing, is what closes it; the details'
 * own pages are more specific and win. `lib/config/detail-slot-source.test.ts`
 * pins this file.
 */
export default function NoDetailHere() {
  return null
}
