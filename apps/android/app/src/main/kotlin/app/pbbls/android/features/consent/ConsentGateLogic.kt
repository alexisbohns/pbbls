package app.pbbls.android.features.consent

import app.pbbls.android.core.model.ActiveConsent
import app.pbbls.android.core.model.ConsentKind
import app.pbbls.android.core.model.LegalVersions

/**
 * What the consent gate asks for, and whether a ledger already answers it.
 * Pure, so the rule that decides whether someone can use the app is tested
 * without a device or a network (design §5.2).
 */
object ConsentGateLogic {
    /** Every act a signed-in Android user must have on record, in the order the gate shows them. */
    val REQUIRED: List<ConsentKind> =
        listOf(ConsentKind.TERMS, ConsentKind.PRIVACY, ConsentKind.HEALTH_DATA, ConsentKind.AGE_ASSURANCE)

    /** The document version this build records [kind] against. */
    fun versionFor(kind: ConsentKind): String =
        when (kind) {
            ConsentKind.TERMS -> LegalVersions.TERMS
            ConsentKind.PRIVACY, ConsentKind.HEALTH_DATA, ConsentKind.AGE_ASSURANCE -> LegalVersions.PRIVACY
        }

    /**
     * The required kinds [active] does not satisfy, in display order. A row
     * satisfies when its version is the same as or NEWER than this build's
     * (design D8): an older app must never re-ask, and so never supersede, a
     * newer acceptance recorded from web.
     */
    fun missing(active: List<ActiveConsent>): List<ConsentKind> =
        REQUIRED.filter { kind ->
            active.none { it.kind == kind.wire && isAtLeast(it.documentVersion, versionFor(kind)) }
        }

    /** Numeric semver comparison. An unparsable version satisfies nothing. */
    fun isAtLeast(
        version: String,
        required: String,
    ): Boolean {
        val have = parse(version) ?: return false
        val need = parse(required) ?: return false
        for (i in 0 until 3) {
            if (have[i] != need[i]) return have[i] > need[i]
        }
        return true
    }

    /**
     * Identifies the set of versions this build requires. A passed check is
     * cached against it (design D7), so any version bump invalidates the cache.
     */
    fun fingerprint(): String = REQUIRED.joinToString(",") { "${it.wire}@${versionFor(it)}" }

    /**
     * `user_consents.source` for every act the gate collects, whatever the
     * account type. `source` records where consent was given, not how the
     * account signed up (#1032), so a Google account consenting here is
     * `android_settings` too. `android_register` stays with the acts
     * `handle_new_user` copies from signup metadata, which really were given
     * at signup. Web's gate records `web_settings` and iOS's `ios_settings` by
     * the same rule.
     */
    const val SOURCE = "android_settings"

    private fun parse(version: String): List<Int>? =
        version
            .split('.')
            .takeIf { it.size == 3 }
            ?.map { it.toIntOrNull() ?: return null }
}
