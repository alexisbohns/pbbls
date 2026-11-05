package app.pbbls.android.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class LegalDocTest {
    @Test
    fun `the page opens in the app's language, English for anything but French`() {
        assertEquals("https://www.pbbls.app/docs/terms?lang=en", LegalDoc.TERMS.urlFor("en"))
        assertEquals("https://www.pbbls.app/docs/privacy?lang=fr", LegalDoc.PRIVACY.urlFor("fr"))
        assertEquals("https://www.pbbls.app/docs/terms?lang=en", LegalDoc.TERMS.urlFor("de"))
    }
}
