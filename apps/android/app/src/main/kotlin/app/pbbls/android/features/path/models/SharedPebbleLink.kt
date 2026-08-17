package app.pbbls.android.features.path.models

/**
 * The public share-by-link URL for a pebble (M51). Canonical host (invite-link
 * convention, see ConnectionsService's INVITE_HOST); the uuid is the
 * capability, lowercased to match how the web app prints ids.
 */
object SharedPebbleLink {
    fun url(pebbleId: String): String = "https://www.pbbls.app/p/${pebbleId.lowercase()}"
}
