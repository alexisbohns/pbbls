package app.pbbls.android.core.data

import app.pbbls.android.core.model.OnboardingReason
import javax.inject.Inject
import javax.inject.Singleton

/** What the onboarding leaves for Home's quick composer (#1510 D9), read once. */
data class OnboardingHandoffNote(
    /**
     * The first person named in step 5, pre-attached to the moment; null when none. A name, not an
     * id: the souls' ids are the server's, so Home resolves it by name once its reference lists reload.
     */
    val firstPersonName: String?,
    /** The first reason picked in step 3, which chooses the composer's prompt; null when skipped. */
    val reason: OnboardingReason?,
)

/**
 * A one-shot mailbox between the onboarding entry and Home: the flow [leave]s its note before
 * popping itself, and Home [take]s it on its next resume. Process memory only: a process death in
 * between loses the hand-off, which is fine (D9: dismissing the composer is fine too).
 */
@Singleton
class OnboardingHandoff
    @Inject
    constructor() {
        private var note: OnboardingHandoffNote? = null

        fun leave(value: OnboardingHandoffNote) {
            note = value
        }

        fun take(): OnboardingHandoffNote? = note.also { note = null }
    }
