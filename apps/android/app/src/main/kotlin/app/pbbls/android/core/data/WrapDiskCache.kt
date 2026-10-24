package app.pbbls.android.core.data

import android.content.Context
import android.util.Log
import app.pbbls.android.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** The wraps' copy on the device (#1207, spec §7.1); faked in the tests that need one. */
interface WrapDiskCaching {
    suspend fun read(
        ownerId: String,
        zone: ZoneId,
        page: String,
    ): String?

    suspend fun write(
        ownerId: String,
        zone: ZoneId,
        page: String,
        body: String,
    )

    /** Every user's wraps, on sign-out and account deletion. */
    fun clearAll()
}

/**
 * The last payload of each page, so a cold start shows the wraps before the network answers
 * (#1207, spec §7.1). One file per page under `noBackupFilesDir/path-wraps/v{schema}/{user}/{zone}/`:
 * a change of user, zone or payload format never reads another's file, and a format bump drops
 * the old tree. It is private journal content, so it is never backed up and is wiped whenever the
 * device resolves signed-out. Display only: the server stays the truth, and a failure here is
 * logged and read as a miss.
 */
@Singleton
class WrapDiskCache(
    private val root: File,
    private val io: CoroutineDispatcher,
) : WrapDiskCaching {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @IoDispatcher io: CoroutineDispatcher,
    ) : this(File(context.noBackupFilesDir, DIRECTORY), io)

    @Volatile
    private var prunedOldVersions = false

    override suspend fun read(
        ownerId: String,
        zone: ZoneId,
        page: String,
    ): String? =
        withContext(io) {
            val file = file(ownerId, zone, page)
            try {
                if (file.isFile) file.readText() else null
            } catch (e: IOException) {
                Log.w(TAG, "unreadable wrap $page, read as a miss", e)
                null
            }
        }

    override suspend fun write(
        ownerId: String,
        zone: ZoneId,
        page: String,
        body: String,
    ): Unit =
        withContext(io) {
            val file = file(ownerId, zone, page)
            try {
                pruneOldVersions()
                file.parentFile?.mkdirs()
                // Written beside, then renamed over, so a crash mid-write never leaves half a payload.
                val partial = File(file.parentFile, "${file.name}.partial")
                partial.writeText(body)
                if (!partial.renameTo(file)) {
                    file.delete()
                    if (!partial.renameTo(file)) Log.w(TAG, "could not keep wrap $page")
                }
            } catch (e: IOException) {
                Log.w(TAG, "could not keep wrap $page", e)
            }
        }

    override fun clearAll() {
        if (root.exists() && !root.deleteRecursively()) Log.w(TAG, "could not clear every kept wrap")
    }

    private fun pruneOldVersions() {
        if (prunedOldVersions) return
        prunedOldVersions = true
        root.listFiles()?.filter { it.name != version() }?.forEach { it.deleteRecursively() }
    }

    private fun file(
        ownerId: String,
        zone: ZoneId,
        page: String,
    ): File = File(File(File(File(root, version()), safe(ownerId)), safe(zone.id)), "${safe(page)}.json")

    private fun version() = "v$SCHEMA_VERSION"

    /** Ids and zone names are path-safe already, but a separator must never climb out of the tree. */
    private fun safe(part: String): String = part.replace(Regex("[^A-Za-z0-9._-]"), "_")

    companion object {
        /** Bump when the payload DTOs change shape incompatibly: the old files are dropped, never misread. */
        const val SCHEMA_VERSION = 1
        private const val DIRECTORY = "path-wraps"
        private const val TAG = "wrap-cache"
    }
}
