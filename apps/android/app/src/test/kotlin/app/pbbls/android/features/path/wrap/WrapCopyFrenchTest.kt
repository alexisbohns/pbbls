package app.pbbls.android.features.path.wrap

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.pbbls.android.features.path.wrap.copy.WrapCopy
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/** The week's sentences over the real `values-fr/strings.xml` (#1207, spec §9): French plurals and genders. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "fr")
class WrapCopyFrenchTest {
    private val copy = WrapCopy(ApplicationProvider.getApplicationContext<Application>().resources, Locale.FRENCH)

    @Test
    fun `the line agrees in gender and number`() {
        assertEquals("Onze galets, cinq personnes.", copy.line(11, 5, live = false, hasMoments = true))
        assertEquals("Un galet, une personne pour l’instant.".replace('’', '\''), copy.line(1, 1, live = true, hasMoments = true))
        assertEquals("Rien pour l'instant.", copy.line(0, 0, live = true, hasMoments = false))
    }

    @Test
    fun `the leads and counts read in French`() {
        assertEquals("Surtout joie, puis paix.", copy.emotionsLead(listOf("Joie", "Paix")))
        assertEquals("Amis d'abord, puis identité et famille.", copy.domainsLead(listOf("Amis", "Identité", "Famille")))
        assertEquals("Six clairs, quatre égaux, trois lourds.", copy.weightLead(6, 4, 3))
        assertEquals("De 08:02 à 22:30.", copy.hoursLead(8.04, 22.5))
        assertEquals("une photo", copy.photos(1))
        assertEquals("deux cartes écrites", copy.cards(2))
    }

    @Test
    fun `the month reads in French`() {
        val october = java.time.YearMonth.of(2026, 10)
        assertEquals("dixième partie", copy.monthKicker(october))
        assertEquals("Octobre", copy.monthName(october))
        assertEquals("cinq semaines", copy.weeksHeader(5))
        assertEquals("deux jalons", copy.milestonesHeader(2))
        assertEquals("les six personnes", copy.morePersons(6))
        assertEquals("Imprimé en joie & paix, sur famille.", copy.colophon(listOf("joie", "paix"), listOf("famille")))
    }
}
