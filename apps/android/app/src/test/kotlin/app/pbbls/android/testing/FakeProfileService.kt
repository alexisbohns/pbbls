package app.pbbls.android.testing

import app.pbbls.android.features.glyph.models.GlyphStroke
import app.pbbls.android.features.profile.models.Collection
import app.pbbls.android.services.ProfileRow
import app.pbbls.android.services.ProfileServicing
import java.time.OffsetDateTime

/**
 * In-memory [ProfileServicing] (#848) — Settings' load, save and delete paths,
 * drivable without a live project. See [PebblesTestHarness] for where these live
 * and why.
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

    /** Every glyph id passed to [loadGlyphStrokes], oldest first. */
    val loadGlyphStrokesCalls = mutableListOf<String>()

    var loadProfileCount = 0
        private set

    var loadCollectionsCount = 0
        private set

    var deleteAccountCount = 0
        private set

    private val armed = ArmedFailure()

    /** Thrown by the next call, then cleared. */
    var failNext: Exception?
        get() = armed.next
        set(value) {
            armed.next = value
        }

    override suspend fun loadProfile(): ProfileRow {
        loadProfileCount += 1
        armed.fire()
        return profile
    }

    override suspend fun loadGlyphStrokes(glyphId: String): List<GlyphStroke> {
        loadGlyphStrokesCalls += glyphId
        armed.fire()
        return glyphStrokes
    }

    override suspend fun loadCollections(): List<Collection> {
        loadCollectionsCount += 1
        armed.fire()
        return collections
    }

    override suspend fun saveSettings(
        displayName: String?,
        glyphId: String?,
        password: String?,
    ) {
        saveSettingsCalls += Triple(displayName, glyphId, password)
        armed.fire()
    }

    override suspend fun setHandle(handle: String?) {
        setHandleCalls += handle
        armed.fire()
    }

    override suspend fun setPublicProfile(isPublic: Boolean) {
        setPublicProfileCalls += isPublic
        armed.fire()
    }

    override suspend fun deleteAccount() {
        deleteAccountCount += 1
        armed.fire()
    }
}
