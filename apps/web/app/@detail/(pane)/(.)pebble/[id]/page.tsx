/**
 * A pebble opened from inside the app (#1384, two-area spec D2): this route
 * intercepts `/pebble/<id>` on a soft navigation, so the left area keeps
 * whichever tab it was showing (Home, People, a collection...) instead of
 * moving to the Path. A hard load or a shared link takes the plain page
 * beside it, with the Path on the left. It renders the same module, never a
 * copy: `lib/config/detail-slot-source.test.ts` pins that.
 */
export { default } from "../../pebble/[id]/page"
