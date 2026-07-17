package app.pbbls.android.features.lab.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

/** Pins the optimistic-toggle transitions (M44 design D4) — iOS-verbatim, including the zero-clamp asymmetry. */
class ReactionToggleTest {
    private fun log(
        id: String,
        count: Int,
    ): Log =
        Log(
            id = id,
            species = LogSpecies.FEATURE,
            platform = LogPlatform.ANDROID,
            status = LogStatus.BACKLOG,
            titleEn = "t",
            summaryEn = "s",
            published = true,
            createdAt = OffsetDateTime.parse("2026-04-20T12:00:00Z"),
            reactionCount = count,
        )

    private val a = "11111111-1111-1111-1111-111111111111"
    private val b = "22222222-2222-2222-2222-222222222222"

    @Test
    fun toggleOnAddsIdAndIncrementsOnlyThatLog() {
        val state = ReactionToggle.State(reactedIds = emptySet(), logs = listOf(log(a, 3), log(b, 7)))
        assertFalse(ReactionToggle.wasReacted(state, a))
        val next = ReactionToggle.toggle(state, a)
        assertTrue(a in next.reactedIds)
        assertEquals(4, next.logs.first { it.id == a }.reactionCount)
        assertEquals(7, next.logs.first { it.id == b }.reactionCount)
    }

    @Test
    fun toggleOffRemovesIdAndDecrements() {
        val state = ReactionToggle.State(reactedIds = setOf(a), logs = listOf(log(a, 3)))
        val next = ReactionToggle.toggle(state, a)
        assertFalse(a in next.reactedIds)
        assertEquals(2, next.logs.single().reactionCount)
    }

    @Test
    fun revertAfterFailedReactRestoresExactly() {
        val state = ReactionToggle.State(reactedIds = emptySet(), logs = listOf(log(a, 3)))
        val wasReacted = ReactionToggle.wasReacted(state, a)
        val reverted = ReactionToggle.revert(ReactionToggle.toggle(state, a), a, wasReacted)
        assertEquals(state, reverted)
    }

    @Test
    fun revertAfterFailedUnreactRestoresExactly() {
        val state = ReactionToggle.State(reactedIds = setOf(a), logs = listOf(log(a, 3)))
        val wasReacted = ReactionToggle.wasReacted(state, a)
        val reverted = ReactionToggle.revert(ReactionToggle.toggle(state, a), a, wasReacted)
        assertEquals(state, reverted)
    }

    @Test
    fun decrementClampsAtZeroAndRevertOvershoots() {
        // iOS-verbatim: withAdjustedCount clamps at zero, so unreact on an
        // already-zero row stays 0 and the revert lands on 1 — ported as-is.
        val state = ReactionToggle.State(reactedIds = setOf(a), logs = listOf(log(a, 0)))
        val toggled = ReactionToggle.toggle(state, a)
        assertEquals(0, toggled.logs.single().reactionCount)
        val reverted = ReactionToggle.revert(toggled, a, wasReacted = true)
        assertEquals(1, reverted.logs.single().reactionCount)
    }

    @Test
    fun unknownIdFlipsSetButLeavesLogsUntouched() {
        // iOS adjusts only the list it owns — a reacted id outside that list
        // (e.g. reacting from See-all while Lab holds other rows) is a no-op
        // on the logs.
        val state = ReactionToggle.State(reactedIds = emptySet(), logs = listOf(log(b, 7)))
        val next = ReactionToggle.toggle(state, a)
        assertTrue(a in next.reactedIds)
        assertEquals(state.logs, next.logs)
    }
}
