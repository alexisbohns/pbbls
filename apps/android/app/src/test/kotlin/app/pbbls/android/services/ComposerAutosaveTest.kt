package app.pbbls.android.services

import app.pbbls.android.features.path.models.PebbleDraft
import app.pbbls.android.features.path.models.PebbleDraftPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [ComposerAutosave] is the pure half of the crash-insurance snapshot — the
 * `Context`-touching [ComposerSnapshotStore] is untestable off-device, so the
 * policy lives here and gets covered here.
 *
 * The debounce *delay* is the composer's `LaunchedEffect`; what this class owns
 * is deciding what to write and, crucially, what NOT to write after a clear.
 */
class ComposerAutosaveTest {
    /** Records writes/erases instead of touching SharedPreferences. */
    private class RecordingSink : ComposerAutosave.SnapshotSink {
        var written: PebbleDraftPayload? = null
        var writeCount = 0
        var eraseCount = 0

        override fun write(payload: PebbleDraftPayload) {
            written = payload
            writeCount++
        }

        override fun erase() {
            written = null
            eraseCount++
        }
    }

    private fun payload(name: String) = PebbleDraftPayload.from(PebbleDraft(name = name))

    @Test
    fun `staging alone does not write`() {
        val sink = RecordingSink()
        ComposerAutosave(sink).stage(payload("Typing"))

        assertEquals(0, sink.writeCount)
        assertNull(sink.written)
    }

    @Test
    fun `flush writes the staged snapshot`() {
        val sink = RecordingSink()
        val autosave = ComposerAutosave(sink)

        autosave.stage(payload("Backgrounded mid-sentence"))
        autosave.flush()

        assertEquals(1, sink.writeCount)
        assertEquals("Backgrounded mid-sentence", sink.written?.name)
    }

    @Test
    fun `rapid keystrokes collapse into one write of the final value`() {
        val sink = RecordingSink()
        val autosave = ComposerAutosave(sink)

        for (name in listOf("A", "Ar", "Arg", "Argu")) autosave.stage(payload(name))
        autosave.flush()

        assertEquals(1, sink.writeCount)
        assertEquals("Argu", sink.written?.name)
    }

    @Test
    fun `flush with nothing staged does not write`() {
        val sink = RecordingSink()
        ComposerAutosave(sink).flush()
        assertEquals(0, sink.writeCount)
    }

    @Test
    fun `flushing twice writes once - the staged value is consumed`() {
        val sink = RecordingSink()
        val autosave = ComposerAutosave(sink)

        autosave.stage(payload("Once"))
        autosave.flush()
        autosave.flush()

        assertEquals(1, sink.writeCount)
    }

    @Test
    fun `an empty payload is never staged`() {
        val sink = RecordingSink()
        val autosave = ComposerAutosave(sink)

        autosave.stage(PebbleDraftPayload.from(PebbleDraft()))
        autosave.flush()

        assertEquals("a blank composer is not worth a restore prompt", 0, sink.writeCount)
    }

    @Test
    fun `clear erases and drops the staged value so a later flush cannot resurrect it`() {
        val sink = RecordingSink()
        val autosave = ComposerAutosave(sink)

        autosave.stage(payload("About to publish"))
        autosave.clear()
        autosave.flush()

        // Regression guard: if the staged value survived clear(), a published
        // pebble would be re-offered as an unsaved draft on the next open.
        assertEquals(1, sink.eraseCount)
        assertEquals(0, sink.writeCount)
        assertNull(sink.written)
    }
}
