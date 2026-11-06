package app.pbbls.android.core.common

/**
 * `take(limit)` that never leaves half an emoji: a cut between the two halves of a surrogate pair
 * leaves a lone high surrogate, which is not valid in the JSON `save_moment` sends, and Postgres' jsonb
 * refuses the whole save. The pair is dropped whole, so the result may be one char under [limit].
 */
fun String.takeSafely(limit: Int): String {
    val cut = take(limit)
    return if (cut.isNotEmpty() && cut.length < length && cut.last().isHighSurrogate()) cut.dropLast(1) else cut
}
