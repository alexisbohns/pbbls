package app.pbbls.android.core.data

import app.pbbls.android.core.model.OnboardingReason
import javax.inject.Inject
import javax.inject.Singleton

/** What the onboarding leaves for Home's quick composer (#1510 D9), read once. */
data class OnboardingHandoffNote(
    /** Who finished the flow: Home takes the note only for the same signed-in user. */
    val userId: String?,
    /** The first reason picked in step 3, which chooses the composer's prompt; null when skipped. */
    val reason: OnboardingReason?,
)

/**
 * A one-shot mailbox between the onboarding entry and Home: the flow [leave]s its note before
 * popping itself, and Home [take]s it on its next resume. Process memory only: a process death in
 * between loses the hand-off, which is fine (D9: dismissing the composer is fine too).
 *
 * The note is bound to its user: [take] hands it over only to the same non-null user id, and
 * discards it otherwise, so a session that ends between the two never opens B's composer with
 * A's prompt.
 */
@Singleton
class OnboardingHandoff
    @Inject
    constructor() {
        private var note: OnboardingHandoffNote? = null

        fun leave(value: OnboardingHandoffNote) {
            note = value
        }

        /** The note left for [userId], or null; either way the mailbox is empty afterwards. */
        fun take(userId: String?): OnboardingHandoffNote? {
            val taken = note
            note = null
            return taken?.takeIf { userId != null && it.userId == userId }
        }
    }
