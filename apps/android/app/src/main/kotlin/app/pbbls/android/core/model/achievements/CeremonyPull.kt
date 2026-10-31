package app.pbbls.android.core.model.achievements

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One row of `pull_ceremony(p_stack, p_tz)` (#1305, spec §3.4). */
@Serializable
data class CeremonyRowDto(
    val stack: String? = null,
    @SerialName("karma_granted")
    val karmaGranted: Int = 0,
)

/**
 * What a pull did. `not_ready` is not here: it arrives as a thrown `DataError.Conflict("not_ready")`
 * (the client's view is stale, so it re-reads and shows nothing).
 */
sealed interface CeremonyPull {
    /** This call pulled it. [karmaGranted] is what the server paid, 0 while the catalog row is unpriced. */
    data class Pulled(
        val stack: CompletionStack,
        val karmaGranted: Int,
    ) : CeremonyPull

    /**
     * No row: the completion was already pulled, by another call, another device, or an earlier
     * pull whose answer was lost. Never read as success: the client re-reads the page, and plays
     * the ceremony without a karma line only if the completion now carries `pulled_at`.
     */
    data object AlreadyPulled : CeremonyPull
}

/** The pull's answer: one row is a pull, no row is [CeremonyPull.AlreadyPulled]. */
fun ceremonyPullOf(
    stack: CompletionStack,
    rows: List<CeremonyRowDto>,
): CeremonyPull = rows.firstOrNull()?.let { CeremonyPull.Pulled(stack, it.karmaGranted) } ?: CeremonyPull.AlreadyPulled
