package app.pbbls.android.features.path.models

import org.junit.Assert.assertEquals
import org.junit.Test

class SharedPebbleLinkTest {
    @Test
    fun `builds the canonical lowercase p URL`() {
        assertEquals(
            "https://www.pbbls.app/p/bc74ba6f-a1f6-4e8c-881b-cf0488d647f7",
            SharedPebbleLink.url("BC74BA6F-A1F6-4E8C-881B-CF0488D647F7"),
        )
    }
}
