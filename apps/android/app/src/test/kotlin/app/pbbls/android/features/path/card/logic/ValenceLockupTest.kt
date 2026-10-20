package app.pbbls.android.features.path.card.logic

import app.pbbls.android.R
import app.pbbls.android.core.model.Valence
import org.junit.Assert.assertEquals
import org.junit.Test

class ValenceLockupTest {
    @Test
    fun `every valence maps to the record flow's lockup`() {
        val expected =
            mapOf(
                Valence.HIGHLIGHT_LARGE to
                    ValenceLockup(R.string.valence_highlight, false, 1f, R.string.valence_span_month, LockupInk.STONE_RAMP),
                Valence.HIGHLIGHT_MEDIUM to
                    ValenceLockup(R.string.valence_highlight, false, 44f / 56f, R.string.valence_span_week, LockupInk.STONE_RAMP),
                Valence.HIGHLIGHT_SMALL to
                    ValenceLockup(R.string.valence_highlight, true, 34f / 56f, R.string.valence_span_day, LockupInk.STONE_RAMP),
                Valence.NEUTRAL_LARGE to
                    ValenceLockup(R.string.valence_headline_neutral, false, 1f, R.string.valence_span_month, LockupInk.PRIMARY),
                Valence.NEUTRAL_MEDIUM to
                    ValenceLockup(R.string.valence_headline_neutral, false, 44f / 56f, R.string.valence_span_week, LockupInk.PRIMARY),
                Valence.NEUTRAL_SMALL to
                    ValenceLockup(R.string.valence_headline_neutral, true, 34f / 56f, R.string.valence_span_day, LockupInk.PRIMARY),
                Valence.LOWLIGHT_LARGE to ValenceLockup(R.string.valence_lowlight, false, 1f, R.string.valence_span_month, LockupInk.INK),
                Valence.LOWLIGHT_MEDIUM to
                    ValenceLockup(R.string.valence_lowlight, false, 44f / 56f, R.string.valence_span_week, LockupInk.INK),
                Valence.LOWLIGHT_SMALL to
                    ValenceLockup(R.string.valence_lowlight, true, 34f / 56f, R.string.valence_span_day, LockupInk.INK),
            )
        Valence.entries.forEach { assertEquals(it.name, expected.getValue(it), ValenceLockups.of(it)) }
    }
}
