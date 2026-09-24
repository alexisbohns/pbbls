package app.pbbls.android.core.designsystem

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class SpacingTest {
    @Test
    fun `spacing is the m3 4 dp grid`() {
        assertEquals(
            listOf(4.dp, 8.dp, 12.dp, 16.dp, 24.dp, 32.dp),
            listOf(Spacing.xs, Spacing.sm, Spacing.md, Spacing.lg, Spacing.xl, Spacing.xxl),
        )
    }
}
