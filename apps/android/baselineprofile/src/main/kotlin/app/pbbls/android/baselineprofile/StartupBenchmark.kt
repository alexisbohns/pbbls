package app.pbbls.android.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Cold-start time with and without the shipped profile (#856).
 *
 * `None` is a fresh install before ART has compiled anything — what a user gets
 * on the first launches after an install or an update when no profile ships.
 * `Partial(Require)` installs the committed profile first, and fails if there
 * is none, so a missing profile cannot pass as a slow result.
 *
 * Run by hand: `./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest`.
 * Signed in, it times the launch to Path; the first iteration signs in, in the
 * setup block, so no measured launch includes typing.
 */
@RunWith(Parameterized::class)
class StartupBenchmark(
    private val compilationMode: CompilationMode,
) {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStart() =
        rule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = ITERATIONS,
            setupBlock = {
                // Sign in once, outside the measured launch.
                startActivityAndWait()
                ensureSignedIn(testAccount())
                pressHome()
            },
        ) {
            startActivityAndWait()
            awaitLanding()
        }

    companion object {
        private const val ITERATIONS = 10

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun modes(): List<CompilationMode> =
            listOf(
                CompilationMode.None(),
                CompilationMode.Partial(BaselineProfileMode.Require),
            )
    }
}
