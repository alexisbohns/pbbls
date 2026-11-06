package app.pbbls.android.core.ui.souls

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.pbbls.android.core.model.SoulWithGlyph

/** Which of a usual soul's two places (S1) was long-pressed: it is a tile and a row. */
enum class SoulChooserSlot { TILE, ROW }

sealed interface SoulChooserMode {
    /** Tiles and rows toggle; the tray shows pills and a button. [allowsSkip]: "Skip" when empty (record flow), "Done" (edit sheet). */
    data class Pick(
        val allowsSkip: Boolean,
    ) : SoulChooserMode

    /** The People tab: tiles and rows open a person, no selection. */
    data object Browse : SoulChooserMode
}

sealed interface SoulChooserButton {
    data class Done(
        val count: Int,
    ) : SoulChooserButton

    data object DoneEmpty : SoulChooserButton

    data object Skip : SoulChooserButton
}

/** The chooser's selection, search and list, over souls the host already fetched (souls spec S2). */
@Stable
class SoulChooserState(
    val mode: SoulChooserMode,
    souls: List<SoulWithGlyph>,
    selection: List<String> = emptyList(),
    query: String = "",
    isSearching: Boolean = false,
) {
    var souls: List<SoulWithGlyph> by mutableStateOf(souls)
        private set

    /** Ordered as chosen, so the pills keep their order. */
    var selection: List<String> by mutableStateOf(selection)
        private set
    var query: String by mutableStateOf(query)
        private set
    var isSearching: Boolean by mutableStateOf(isSearching)
        private set

    /** The failed insert's line under the field (S4). */
    @get:StringRes
    var createErrorRes: Int? by mutableStateOf(null)

    // Cached: a tap changes the selection only, and must not re-sort the whole list.
    private val usualCache = derivedStateOf { SoulRanking.usual(this.souls) }
    private val sectionsCache = derivedStateOf { SoulRanking.sections(this.souls) }
    private val resultsCache = derivedStateOf { SoulRanking.results(this.souls, this.query) }
    private val canAddCache = derivedStateOf { SoulRanking.canAdd(this.souls, this.query) }

    val usual: List<SoulWithGlyph> get() = usualCache.value
    val sections: List<SoulSection> get() = sectionsCache.value
    val results: List<SoulWithGlyph> get() = resultsCache.value
    val canAdd: Boolean get() = canAddCache.value
    val trimmedQuery: String get() = query.trim()

    fun isSelected(id: String): Boolean = id in selection

    fun toggle(id: String) {
        if (mode !is SoulChooserMode.Pick) return
        selection = if (id in selection) selection - id else selection + id
    }

    fun remove(id: String) {
        if (mode is SoulChooserMode.Pick) selection = selection - id
    }

    val button: SoulChooserButton?
        get() =
            when (mode) {
                SoulChooserMode.Browse -> null
                is SoulChooserMode.Pick ->
                    when {
                        selection.isNotEmpty() -> SoulChooserButton.Done(selection.size)
                        mode.allowsSkip -> SoulChooserButton.Skip
                        else -> SoulChooserButton.DoneEmpty
                    }
            }

    /** A fresh fetch: a chosen soul that is gone costs a pill, not a `soul_not_owned`. */
    fun replaceSouls(fresh: List<SoulWithGlyph>) {
        souls = fresh
        val known = fresh.mapTo(HashSet()) { it.id }
        selection = selection.filter { it in known }
    }

    /** [replaceSouls] only once the list is a real fetch: an unloaded empty list must not cost a pill. */
    fun replaceSoulsWhenLoaded(
        fresh: List<SoulWithGlyph>,
        isLoaded: Boolean,
    ) {
        if (isLoaded) replaceSouls(fresh)
    }

    fun didCreate(soul: SoulWithGlyph) {
        if (souls.none { it.id == soul.id }) souls = souls + soul
        if (mode is SoulChooserMode.Pick && soul.id !in selection) selection = selection + soul.id
        closeSearch()
    }

    /** Typing: a new query drops the previous failed create's line (S4). */
    fun updateQuery(text: String) {
        query = text
        createErrorRes = null
    }

    fun clearQuery() = updateQuery("")

    fun openSearch() {
        isSearching = true
        createErrorRes = null
    }

    /** Closing the field clears the query (S3). */
    fun closeSearch() {
        clearQuery()
        isSearching = false
    }

    companion object {
        /**
         * Saves what the host cannot refetch: the chosen ids in order, the query and the open
         * field. [souls] come back from the host, so restore starts from [souls] and the first
         * loaded fetch prunes the selection.
         */
        fun saver(
            mode: SoulChooserMode,
            souls: List<SoulWithGlyph>,
        ): Saver<SoulChooserState, Any> =
            listSaver(
                save = { listOf(ArrayList(it.selection), it.query, it.isSearching) },
                restore = { saved ->
                    @Suppress("UNCHECKED_CAST")
                    SoulChooserState(mode, souls, saved[0] as List<String>, saved[1] as String, saved[2] as Boolean)
                },
            )
    }
}

/**
 * Remembers a [SoulChooserState]; a rotation keeps the selection, the query and the open field
 * through [SoulChooserState.saver], and a different [mode] gets a fresh state. Nothing is written
 * back during composition, so a tap does not recompose the host.
 *
 * [souls] flowing in later (a refresh) goes through [SoulChooserState.replaceSoulsWhenLoaded]:
 * until [isLoaded] the list is only a placeholder and the selection is left alone. [isLoaded] has
 * no default on purpose: a host that forgets it would defeat the guard.
 */
@Composable
fun rememberSoulChooserState(
    mode: SoulChooserMode,
    souls: List<SoulWithGlyph>,
    isLoaded: Boolean,
    selection: List<String> = emptyList(),
): SoulChooserState {
    val state =
        rememberSaveable(mode, saver = SoulChooserState.saver(mode, souls)) {
            SoulChooserState(mode, souls, selection)
        }
    LaunchedEffect(souls, isLoaded) { state.replaceSoulsWhenLoaded(souls, isLoaded) }
    return state
}
