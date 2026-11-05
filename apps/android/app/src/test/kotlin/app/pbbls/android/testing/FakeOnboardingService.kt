package app.pbbls.android.testing

import app.pbbls.android.core.data.OnboardingServicing
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.OnboardingAnswers
import app.pbbls.android.core.model.PromiseLetter

/** In-memory [OnboardingServicing]: the letter, eight system glyphs, and every [complete] call recorded. */
class FakeOnboardingService(
    var letter: PromiseLetter? = PromiseLetter(bodyEn = "There will never be a feed here.", signedByEn = "The Pebbles team"),
    var glyphs: List<Glyph> = List(8) { Glyph(id = "sys-$it", strokes = emptyList(), viewBox = "0 0 200 200", isSystem = true) },
) : OnboardingServicing {
    val completeCalls = mutableListOf<OnboardingAnswers>()
    var letterFailure: Exception? = null
    var glyphsFailure: Exception? = null
    var completeFailure: Exception? = null

    var sessionAwaited = false

    override suspend fun awaitSession() {
        sessionAwaited = true
    }

    override suspend fun promiseLetter(): PromiseLetter? {
        letterFailure?.let { throw it }
        return letter
    }

    override suspend fun systemGlyphs(): List<Glyph> {
        glyphsFailure?.let { throw it }
        return glyphs
    }

    override suspend fun complete(answers: OnboardingAnswers) {
        completeFailure?.let { throw it }
        completeCalls += answers
    }
}
