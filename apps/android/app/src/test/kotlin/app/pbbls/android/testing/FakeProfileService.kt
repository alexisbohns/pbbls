package app.pbbls.android.testing

import app.pbbls.android.features.glyph.models.GlyphStroke
import app.pbbls.android.features.profile.models.Collection
import app.pbbls.android.services.ProfileRow
import app.pbbls.android.services.ProfileServicing
import java.time.OffsetDateTime

/**
 * In-memory [ProfileServicing] for tests.
 *
 * It holds no `SupabaseClient` and reads no `BuildConfig` — that is the whole
 * point: Settings' load, save and delete paths are drivable without a live
 * project (#848).
 *
 * Lives in `src/test` because that is the only consumer until #857 lands
 * Robolectric; it moves to a real `core/testing` module with #851.
 */
class FakeProfileService(
    var profile: ProfileRow =
        ProfileRow(
            displayName = "Pebbler",
            createdAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
        ),
    var glyphStrokes: List<GlyphStroke> = emptyList(),
    var collections: List<Collection> = emptyList(),
) : ProfileServicing {
    /** Arguments of every [saveSettings] call, oldest first. */
    val saveSettingsCalls = mutableListOf<Triple<String?, String?, String?>>()

    /** Every handle passed to [setHandle] (null means "release"), oldest first. */
    val setHandleCalls = mutableListOf<String?>()

    /** Every flag passed to [setPublicProfile], oldest first. */
    val setPublicProfileCalls = mutableListOf<Boolean>()

    var loadProfileCount = 0
        private set

    var deleteAccountCount = 0
        private set

    /**
     * Thrown by the next call, then cleared — so one fake can drive a failure
     * and the retry that follows it without being rebuilt.
     */
    var failNext: Exception? = null

    override suspend fun loadProfile(): ProfileRow {
        loadProfileCount += 1
        throwIfArmed()
        return profile
    }

    override suspend fun loadGlyphStrokes(glyphId: String): List<GlyphStroke> {
        throwIfArmed()
        return glyphStrokes
    }

    override suspend fun loadCollections(): List<Collection> {
        throwIfArmed()
        return collections
    }

    override suspend fun saveSettings(
        displayName: String?,
        glyphId: String?,
        password: String?,
    ) {
        saveSettingsCalls += Triple(displayName, glyphId, password)
        throwIfArmed()
    }

    override suspend fun setHandle(handle: String?) {
        setHandleCalls += handle
        throwIfArmed()
    }

    override suspend fun setPublicProfile(isPublic: Boolean) {
        setPublicProfileCalls += isPublic
        throwIfArmed()
    }

    override suspend fun deleteAccount() {
        deleteAccountCount += 1
        throwIfArmed()
    }

    private fun throwIfArmed() {
        val failure = failNext ?: return
        failNext = null
        throw failure
    }
}
