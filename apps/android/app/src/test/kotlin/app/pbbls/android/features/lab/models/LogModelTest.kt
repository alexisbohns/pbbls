package app.pbbls.android.features.lab.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.OffsetDateTime
import java.util.Locale

/** Localized accessors + the count clamp + the cover URL guard (M44 design D5/D7 edges of the model). */
class LogModelTest {
    private fun log(
        titleFr: String? = null,
        summaryFr: String? = null,
        bodyMdEn: String? = null,
        bodyMdFr: String? = null,
        count: Int = 0,
    ): Log =
        Log(
            id = "11111111-1111-1111-1111-111111111111",
            species = LogSpecies.FEATURE,
            platform = LogPlatform.ALL,
            status = LogStatus.SHIPPED,
            titleEn = "Title EN",
            titleFr = titleFr,
            summaryEn = "Summary EN",
            summaryFr = summaryFr,
            bodyMdEn = bodyMdEn,
            bodyMdFr = bodyMdFr,
            published = true,
            createdAt = OffsetDateTime.parse("2026-04-20T12:00:00Z"),
            reactionCount = count,
        )

    @Test
    fun frenchLocalePrefersFrenchValues() {
        val log = log(titleFr = "Titre FR", summaryFr = "Résumé FR", bodyMdEn = "body en", bodyMdFr = "corps fr")
        assertEquals("Titre FR", log.title(Locale.FRENCH))
        assertEquals("Résumé FR", log.summary(Locale.FRENCH))
        assertEquals("corps fr", log.body(Locale.FRENCH))
    }

    @Test
    fun frenchLocaleFallsBackToEnglishWhenFrenchNull() {
        val log = log(bodyMdEn = "body en")
        assertEquals("Title EN", log.title(Locale.FRENCH))
        assertEquals("Summary EN", log.summary(Locale.FRENCH))
        assertEquals("body en", log.body(Locale.FRENCH))
    }

    @Test
    fun englishLocaleIgnoresFrenchValues() {
        val log = log(titleFr = "Titre FR", summaryFr = "Résumé FR", bodyMdFr = "corps fr", bodyMdEn = "body en")
        assertEquals("Title EN", log.title(Locale.ENGLISH))
        assertEquals("Summary EN", log.summary(Locale.ENGLISH))
        assertEquals("body en", log.body(Locale.ENGLISH))
    }

    @Test
    fun bodyIsNullWhenBothVariantsNull() {
        assertNull(log().body(Locale.ENGLISH))
        assertNull(log().body(Locale.FRENCH))
    }

    @Test
    fun withAdjustedCountClampsAtZero() {
        assertEquals(0, log(count = 0).withAdjustedCount(-1).reactionCount)
        assertEquals(2, log(count = 3).withAdjustedCount(-1).reactionCount)
        assertEquals(1, log(count = 0).withAdjustedCount(+1).reactionCount)
    }

    @Test
    fun coverImageUrlGuardsNullAndEmpty() {
        assertNull(LabConfig.coverImageUrl("https://x.supabase.co", null))
        assertNull(LabConfig.coverImageUrl("https://x.supabase.co", ""))
        assertEquals(
            "https://x.supabase.co/storage/v1/object/public/lab-assets/covers/a.png",
            LabConfig.coverImageUrl("https://x.supabase.co/", "covers/a.png"),
        )
    }
}
