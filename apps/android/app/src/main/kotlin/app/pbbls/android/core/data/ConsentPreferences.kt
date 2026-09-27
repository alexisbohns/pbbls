package app.pbbls.android.core.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers that the consent gate passed, so a consented user is not held
 * behind a network round trip on every launch, and is not locked out when they
 * launch offline (design D7).
 *
 * One entry, `"<userId>|<fingerprint>"`, overwritten by the next pass: the
 * device keeps no history of who signed in on it. The fingerprint is every
 * required kind at its version (`ConsentGateLogic.fingerprint`), so a version
 * bump misses the cache and the gate asks again.
 */
@Singleton
class ConsentPreferences internal constructor(
    private val prefs: SharedPreferences,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

    fun isSatisfied(
        userId: String,
        fingerprint: String,
    ): Boolean = prefs.getString(KEY_SATISFIED, null) == entry(userId, fingerprint)

    fun markSatisfied(
        userId: String,
        fingerprint: String,
    ) {
        prefs.edit { putString(KEY_SATISFIED, entry(userId, fingerprint)) }
    }

    private fun entry(
        userId: String,
        fingerprint: String,
    ) = "$userId|$fingerprint"

    private companion object {
        /** Shared with OnboardingPreferences and AppearancePreferences: one prefs file for the app. */
        const val PREFS_NAME = "pebbles_prefs"
        const val KEY_SATISFIED = "consentGateSatisfied"
    }
}
