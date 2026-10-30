package app.pbbls.android.features.achievements.copy

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.pbbls.android.core.model.achievements.CompletionStack
import app.pbbls.android.core.model.achievements.CountingSection
import app.pbbls.android.core.model.achievements.Coverage
import app.pbbls.android.core.model.achievements.Needs
import app.pbbls.android.core.model.achievements.NextLevel
import app.pbbls.android.core.model.achievements.NextStep
import app.pbbls.android.core.model.achievements.StackLevel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/** The same sentences over `values-fr/strings.xml` (#1307): iOS's French, plurals (one, many, other) and genders. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "fr")
class AchievementsCopyFrenchTest {
    private val copy = AchievementsCopy(ApplicationProvider.getApplicationContext<Application>().resources, Locale.FRENCH)

    @Test
    fun `the next step reads as iOS words it`() {
        assertEquals(
            "Douze jours de plus avec un moment, un dans chaque semaine complète : novembre rejoindra le disque intérieur.",
            copy.nextStep(NextStep(11, Needs.MOMENTS, daysLeft = 12, reachable = true)),
        )
        assertEquals(
            "Un jour de plus avec un galet, et un dans chaque semaine complète : mars atteindra le bord.",
            copy.nextStep(NextStep(3, Needs.PEBBLES, daysLeft = 1, reachable = true)),
        )
        assertEquals("Juin n'atteindra pas le bord ce mois-ci.", copy.nextStep(NextStep(6, Needs.PEBBLES, daysLeft = 4, reachable = false)))
    }

    @Test
    fun `counts read as French words, stones are feminine`() {
        assertEquals("cinq sur huit", copy.ofWords(5, 8))
        assertEquals("Tes galets touchent la plupart des parts de ta vie. Tes aperçus ont de quoi lire.", copy.coverage(Coverage.MOST))
        assertEquals("novembre", copy.monthInSentence(11))
        assertEquals("Novembre", copy.monthHeading(11))
        val pebbles =
            CountingSection(
                137,
                listOf(1, 10, 25, 50, 100).map { StackLevel(it, null, null, false) },
                NextLevel(250, 113, 0.25f),
                listOf(1, 10, 25, 50, 100, 250, 500, 1000),
                thenEvery = 500,
                since = null,
            )
        assertEquals("137 galets. Cinq pierres se dressent ; la sixième s'élève vers 250.", copy.pebblesLine(pebbles))
        assertEquals("jour", copy.daysUnit(1))
        assertEquals("jours", copy.daysUnit(249))
    }

    @Test
    fun `the ceremonies are titled as the catalog titles them`() {
        assertEquals("Toutes les émotions", copy.ceremonyTitle(CompletionStack.EMOTIONS))
        assertEquals("Tous les domaines", copy.ceremonyTitle(CompletionStack.DOMAINS))
    }
}
