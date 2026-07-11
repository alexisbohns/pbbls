package app.pbbls.android.features.auth

/**
 * Pure auth-form logic, extracted so it is unit-tested to iOS parity without a
 * live client — the `AuthView.canSubmit` / `AuthView.normalizeEmailInput`
 * analogs (`apps/ios/Pebbles/Features/Auth/AuthView.swift`).
 */
object AuthLogic {
    /**
     * Whether the submit button is enabled. Email must be non-blank, contain
     * `@` and `.`, and contain no `+`; password ≥ 6; sign-up additionally
     * requires both consent checkboxes. Always false while a request is in flight.
     */
    fun canSubmit(
        mode: AuthMode,
        email: String,
        password: String,
        termsAccepted: Boolean,
        privacyAccepted: Boolean,
        isSubmitting: Boolean,
    ): Boolean {
        val trimmed = email.trim()
        if (isSubmitting) return false
        if (trimmed.isEmpty()) return false
        if (!trimmed.contains("@")) return false
        if (!trimmed.contains(".")) return false
        if (trimmed.contains("+")) return false
        if (password.length < 6) return false
        return if (mode == AuthMode.SIGNUP) termsAccepted && privacyAccepted else true
    }

    /**
     * Lowercases and strips disallowed characters (`+`) from raw email input.
     * Real-time scrubbing keeps the visible field, the submitted value, and the
     * server's view of the address in sync.
     */
    fun normalizeEmailInput(raw: String): String = raw.lowercase().filter { it != '+' }
}
