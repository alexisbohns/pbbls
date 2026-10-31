package app.pbbls.android.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.core.common.UiEffects
import app.pbbls.android.core.data.AchievementsServicing
import app.pbbls.android.core.data.MomentWriteServicing
import app.pbbls.android.features.home.quick.QuickMoment
import app.pbbls.android.features.home.quick.QuickMomentExit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** What Home does once one of its own flows has written a moment (#1383 D7). */
sealed interface HomeMomentEffect {
    /** The quick moment saved: Home reloads, and opens Complete for [exit] [QuickMomentExit.OPEN]. */
    data class QuickSaved(
        val momentId: String,
        val exit: QuickMomentExit,
    ) : HomeMomentEffect
}

/**
 * The writes Home starts itself (#1383 D7): the quick moment's sheet, and the camera moment. Scoped to
 * Home's entry like [HomeViewModel], beside it rather than inside it: [HomeViewModel] owns Home's reads
 * and keeps its constructor to them, this one owns the two flows' state holders and runs their writes
 * in its `viewModelScope`, so a save outlives the sheet that started it.
 *
 * The sheet is not a navigation key (iOS presents it over Home, and Android draws it as a
 * `ModalBottomSheet` there), so there is no entry to scope a sheet-only ViewModel to: [quick] is a
 * plain holder, fresh each time the sheet opens. After any save, Home reloads what it shows through
 * [HomeViewModel.onMomentSaved]; the Path re-reads on its own resume (#852).
 */
@HiltViewModel
class HomeMomentsViewModel
    @Inject
    constructor(
        writes: MomentWriteServicing,
        achievements: AchievementsServicing,
    ) : ViewModel() {
        private val effectsOut = UiEffects<HomeMomentEffect>(viewModelScope)
        val effects: Flow<HomeMomentEffect> = effectsOut.flow

        val quick =
            QuickMoment(
                scope = viewModelScope,
                writes = writes,
                achievements = achievements,
                onSaved = { id, exit -> effectsOut.emit(HomeMomentEffect.QuickSaved(id, exit)) },
            )
    }
