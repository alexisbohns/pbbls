package app.pbbls.android.features.path.card.logic

import androidx.annotation.StringRes
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.features.path.valence.valenceSpanRes
import app.pbbls.android.features.path.valence.valenceWordLowercase
import app.pbbls.android.features.path.valence.valenceWordRes
import app.pbbls.android.features.path.valence.valenceWordScale

/** How the verso's lockup paints its word (spec §Verso 1). */
enum class LockupInk { STONE_RAMP, INK, PRIMARY }

/** The verso's top, as the record flow's lockup states it (`ValenceWord` + span). */
data class ValenceLockup(
    @StringRes val wordRes: Int,
    val lowercase: Boolean,
    val scale: Float,
    @StringRes val spanRes: Int,
    val ink: LockupInk,
)

object ValenceLockups {
    fun of(valence: Valence): ValenceLockup =
        ValenceLockup(
            wordRes = valenceWordRes(valence.polarity),
            lowercase = valenceWordLowercase(valence.sizeGroup),
            scale = valenceWordScale(valence.sizeGroup),
            spanRes = valenceSpanRes(valence.sizeGroup),
            ink =
                when (valence.polarity) {
                    ValencePolarity.HIGHLIGHT -> LockupInk.STONE_RAMP
                    ValencePolarity.NEUTRAL -> LockupInk.PRIMARY
                    ValencePolarity.LOWLIGHT -> LockupInk.INK
                },
        )
}
