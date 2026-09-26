package app.pbbls.android.navigation

import androidx.navigation3.runtime.NavKey
import app.pbbls.android.core.model.AuthMode
import app.pbbls.android.core.model.GlyphGridItem
import kotlinx.serialization.Serializable

/**
 * Every navigable destination in the app (#852, supersedes M38 D5).
 *
 * The two marker interfaces below are the whole trick: bar visibility and
 * tab-ness are properties OF THE KEY, read straight off the stack, rather than
 * a second list somewhere that can disagree with the first. A new modal key is
 * safe by default because it simply is not a [BarKey].
 *
 * Keys are `@Serializable` because `rememberNavBackStack` persists them across
 * process death — that persistence is the whole reason the covers are being
 * promoted, so a key that cannot serialize defeats the migration.
 */
@Serializable
sealed interface PebblesKey : NavKey {
    // ---- Top level: the four tabs (D2) ----

    @Serializable
    data object Path : PebblesKey, TopLevelKey

    @Serializable
    data object People : PebblesKey, TopLevelKey

    @Serializable
    data object Collections : PebblesKey, TopLevelKey

    @Serializable
    data object You : PebblesKey, TopLevelKey

    // ---- Browse pushes: the bar stays up ----

    @Serializable
    data class SoulDetail(
        val soulId: String,
    ) : PebblesKey,
        BarKey

    @Serializable
    data class CollectionDetail(
        val collectionId: String,
    ) : PebblesKey,
        BarKey

    @Serializable
    data object Connections : PebblesKey, BarKey

    @Serializable
    data object Glyphs : PebblesKey, BarKey

    /**
     * One glyph's swap/owned panel (#940): a docked sheet on phones, a pane
     * beside the store on large screens. Carries the grid item itself — there
     * is no by-id read, and opening from the grid must be instant.
     */
    @Serializable
    data class GlyphDetail(
        val item: GlyphGridItem,
    ) : PebblesKey,
        BarKey

    @Serializable
    data object Achievements : PebblesKey, BarKey

    @Serializable
    data object Lab : PebblesKey, BarKey

    @Serializable
    data class LabAnnouncement(
        val logId: String,
    ) : PebblesKey,
        BarKey

    @Serializable
    data class LabLogList(
        val mode: String,
    ) : PebblesKey,
        BarKey

    /**
     * A docked sheet on phones and a pane beside Path on large screens (#940),
     * never a full-screen modal — so the bar stays: under the sheet's scrim on
     * a phone, beside the panes on a tablet. D6 still holds because this is no
     * longer a modal; [EditPebble], opened from it, still is.
     */
    @Serializable
    data class PebbleDetail(
        val pebbleId: String,
    ) : PebblesKey,
        BarKey

    // ---- Modal: full-screen, the bar is covered ----

    @Serializable
    data class RecordFlow(
        val resumeDraftId: String? = null,
    ) : PebblesKey

    @Serializable
    data class CreatePebble(
        val resumeDraftId: String? = null,
    ) : PebblesKey

    @Serializable
    data object Drafts : PebblesKey

    @Serializable
    data class EditPebble(
        val pebbleId: String,
    ) : PebblesKey

    @Serializable
    data object Settings : PebblesKey

    /** [soulId] null means "create". */
    @Serializable
    data class SoulForm(
        val soulId: String? = null,
    ) : PebblesKey

    /** [collectionId] null means "create". */
    @Serializable
    data class CollectionForm(
        val collectionId: String? = null,
    ) : PebblesKey

    @Serializable
    data object Invite : PebblesKey

    @Serializable
    data class AcceptInvite(
        val token: String,
    ) : PebblesKey

    @Serializable
    data object GlyphCarve : PebblesKey

    @Serializable
    data object Onboarding : PebblesKey

    // ---- Unauthenticated funnel (Part 5 makes these reachable) ----

    @Serializable
    data object Welcome : PebblesKey

    /**
     * [mode] is a typed enum, not a route string. This is what deletes
     * `AuthMode.fromRoute`'s silent `LOGIN` default: a malformed mode is now a
     * deserialization failure, not a user silently landing on the wrong tab.
     */
    @Serializable
    data class Auth(
        val mode: AuthMode,
    ) : PebblesKey

    companion object {
        /** Bar order, left to right. Also the tab set [NavigationState] keys on. */
        val tabs: List<PebblesKey> = listOf(Path, People, Collections, You)
    }
}

/** A key that is one of the four bar destinations. Always also a [BarKey]. */
sealed interface TopLevelKey : BarKey

/**
 * A key that renders the [NavigationBar]. Everything else is modal and covers
 * it (D5).
 *
 * **Load-bearing invariant (D6):** tab switching is only reachable from a
 * `BarKey`, which is what guarantees a non-current tab's stack never has a
 * modal on top of it. Making the bar visible over a modal breaks the flattened
 * stack and must be rejected on those grounds.
 */
sealed interface BarKey
