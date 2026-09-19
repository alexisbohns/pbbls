package app.pbbls.android.testing

/**
 * One-shot failure arming, shared by the fakes (#848).
 *
 * Set [next] and the following call throws it, then CLEARS — so a test can drive
 * "fails once, then the retry succeeds" without rebuilding the fake. That
 * clear-once semantic is what `ServiceGraphFakesTest` pins; keeping it in one
 * place means the test covers every fake rather than whichever one it happens to
 * name.
 *
 * Only for methods whose real service genuinely throws. A service that swallows
 * its own errors must NOT get one of these — see [FakeReferenceDataService].
 */
internal class ArmedFailure {
    var next: Exception? = null

    /** Throws the armed exception, if any, and disarms. */
    fun fire() {
        next?.let {
            next = null
            throw it
        }
    }
}
