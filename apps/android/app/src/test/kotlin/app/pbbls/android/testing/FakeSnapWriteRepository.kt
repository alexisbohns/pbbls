package app.pbbls.android.testing

import app.pbbls.android.core.data.ProcessedImage
import app.pbbls.android.core.data.SnapWriteRepositing

/**
 * In-memory [SnapWriteRepositing] (#849).
 *
 * Only the record flow's *empty* photo path is exercised by
 * `RecordFlowViewModelTest`: attaching needs a `Uri`, which cannot be built off
 * device. This exists so the coordinator can be constructed, and so a test that
 * did reach an upload would see it rather than a null.
 */
class FakeSnapWriteRepository : SnapWriteRepositing {
    val uploaded = mutableListOf<String>()
    val deletedSnapIds = mutableListOf<String>()
    val deletedPrefixes = mutableListOf<String>()

    private val armed = ArmedFailure()

    /** Thrown by the next [uploadProcessed], then cleared. */
    var failNext: Exception?
        get() = armed.next
        set(value) {
            armed.next = value
        }

    override suspend fun uploadProcessed(
        processed: ProcessedImage,
        snapId: String,
        userId: String,
    ) {
        uploaded += snapId
        armed.fire()
    }

    override suspend fun deletePebbleMedia(snapId: String): String {
        deletedSnapIds += snapId
        return "u/$snapId"
    }

    override suspend fun deleteFiles(
        snapId: String,
        userId: String,
    ) {
        deletedSnapIds += snapId
    }

    override suspend fun deleteFiles(storagePrefix: String) {
        deletedPrefixes += storagePrefix
    }
}
