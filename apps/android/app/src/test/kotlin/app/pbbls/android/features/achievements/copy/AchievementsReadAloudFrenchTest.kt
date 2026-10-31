package app.pbbls.android.features.achievements.copy

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.pbbls.android.AchievementsFixture
import app.pbbls.android.R
import app.pbbls.android.core.model.achievements.CompletionStack
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.Locale

/** The cards read aloud in French (#1307): iOS's labels, the list joined by " ; ", "nouveauté" plural. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "fr")
class AchievementsReadAloudFrenchTest {
    private val copy = AchievementsCopy(ApplicationProvider.getApplicationContext<Application>().resources, Locale.FRENCH)
    private val page = AchievementsFixture.page

    @Test
    fun `the header and Explorer read as iOS words them`() {
        assertEquals("249 jours, depuis le dimanche 1 mars 2026", copy.headerDescription(249, LocalDate.of(2026, 3, 1)))
        assertEquals(
            "Explorateur : les cinq premières fois, c'est fait ; les six premières fois, c'est fait ; 25 galets, c'est fait ; " +
                "domaines, 14 sur 18 ; émotions, 23 sur 38. 3 nouveautés.",
            copy.explorerDescription(page.explorer!!, page.emotions, page.domains),
        )
    }

    @Test
    fun `Emotions, the year and the ready mark`() {
        assertEquals("Émotions, 23 sur 38. 2 nouveautés.", copy.emotionsDescription(page.emotions!!))
        assertEquals(
            "L'année 2026. Au bord : août. Sur le disque intérieur : mars, avril, mai, juin, juillet, septembre et octobre. " +
                "En chemin : novembre. Douze jours de plus avec un moment, un dans chaque semaine complète : novembre rejoindra le disque intérieur.",
            copy.yearDescription(page.years.first()),
        )
        assertEquals("Toutes les émotions : la cérémonie est prête.", copy.readyDescription(CompletionStack.EMOTIONS))
        assertEquals("prêt", copy.string(R.string.achievements_ready))
    }

    @Test
    fun `the new count is plural - one nouveauté, many nouveautés`() {
        val seats = page.emotions!!.seats
        val one = page.emotions!!.copy(seats = seats.mapIndexed { i, seat -> seat.copy(isNew = i == 0) })
        assertEquals("Émotions, 23 sur 38. 1 nouveauté.", copy.emotionsDescription(one))
    }
}
