package app.pbbls.android

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AppEnvironmentTest {
    @Test
    fun requireSecretReturnsValueWhenPresent() {
        assertEquals(
            "https://example.supabase.co",
            AppEnvironment.requireSecret("SUPABASE_URL", "https://example.supabase.co"),
        )
    }

    @Test
    fun requireSecretThrowsWithSetupInstructionsWhenBlank() {
        val error =
            assertThrows(IllegalStateException::class.java) {
                AppEnvironment.requireSecret("SUPABASE_URL", "")
            }
        assertEquals(
            "SUPABASE_URL is missing. Copy apps/android/secrets.example.properties to " +
                "secrets.properties and fill in real values.",
            error.message,
        )
    }

    // Uses runTest to exercise the kotlinx-coroutines-test harness wired in A,
    // even though the assertion itself is synchronous.
    @Test
    fun requireSecretRejectsWhitespaceOnlyValues() =
        runTest {
            assertThrows(IllegalStateException::class.java) {
                AppEnvironment.requireSecret("SUPABASE_ANON_KEY", "   ")
            }
        }
}
