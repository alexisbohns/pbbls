package app.pbbls.android.core.ui.souls

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.pbbls.android.core.model.SoulWithGlyph

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
) {
    var souls: List<SoulWithGlyph> by mutableStateOf(souls)
        private set

    /** Ordered as chosen, so the pills keep their order. */
    var selection: List<String> by mutableStateOf(selection)
        private set
    var query: String by mutableStateOf("")
    var isSearching: Boolean by mutableStateOf(false)

    /** The failed insert's line under the field (S4). */
    @get:StringRes
    var createErrorRes: Int? by mutableStateOf(null)

    val usual: List<SoulWithGlyph> get() = SoulRanking.usual(souls)
    val sections: List<SoulSection> get() = SoulRanking.sections(souls)
    val results: List<SoulWithGlyph> get() = SoulRanking.results(souls, query)
    val canAdd: Boolean get() = SoulRanking.canAdd(souls, query)
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

    fun didCreate(soul: SoulWithGlyph) {
        if (souls.none { it.id == soul.id }) souls = souls + soul
        if (mode is SoulChooserMode.Pick && soul.id !in selection) selection = selection + soul.id
        closeSearch()
    }

    fun openSearch() {
        isSearching = true
    }

    /** Closing the field clears the query (S3). */
    fun closeSearch() {
        query = ""
        isSearching = false
        createErrorRes = null
    }
}

/**
 * Remembers a [SoulChooserState]; a rotation keeps the selection, the query and the open field.
 * [souls] flowing in later (a refresh) goes through [SoulChooserState.replaceSouls].
 */
@Composable
fun rememberSoulChooserState(
    mode: SoulChooserMode,
    souls: List<SoulWithGlyph>,
    selection: List<String> = emptyList(),
): SoulChooserState {
    var savedSelection by rememberSaveable { mutableStateOf(selection) }
    var savedQuery by rememberSaveable { mutableStateOf("") }
    var savedSearching by rememberSaveable { mutableStateOf(false) }
    val state =
        remember {
            SoulChooserState(mode, souls, savedSelection).also {
                it.query = savedQuery
                it.isSearching = savedSearching
            }
        }
    savedSelection = state.selection
    savedQuery = state.query
    savedSearching = state.isSearching
    LaunchedEffect(souls) { state.replaceSouls(souls) }
    return state
}
