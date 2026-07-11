package app.pbbls.android.features.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ports all 16 iOS `AuthViewLogicTests` cases 1:1
 * (`apps/ios/PebblesTests/Features/Auth/AuthViewLogicTests.swift`) so the
 * Android `canSubmit` / `normalizeEmailInput` stay at parity.
 */
class AuthLogicTest {
    @Test
    fun loginInvalidEmailReturnsFalse() {
        assertFalse(
            AuthLogic.canSubmit(AuthMode.LOGIN, "not-an-email", "password", false, false, false),
        )
    }

    @Test
    fun loginShortPasswordReturnsFalse() {
        assertFalse(
            AuthLogic.canSubmit(AuthMode.LOGIN, "hello@bohns.design", "abc", false, false, false),
        )
    }

    @Test
    fun loginValidCredentialsReturnTrue() {
        assertTrue(
            AuthLogic.canSubmit(AuthMode.LOGIN, "hello@bohns.design", "abcdef", false, false, false),
        )
    }

    @Test
    fun loginIgnoresConsentFlags() {
        assertTrue(
            AuthLogic.canSubmit(AuthMode.LOGIN, "hello@bohns.design", "abcdef", true, true, false),
        )
    }

    @Test
    fun signupMissingTermsReturnsFalse() {
        assertFalse(
            AuthLogic.canSubmit(AuthMode.SIGNUP, "hello@bohns.design", "abcdef", false, true, false),
        )
    }

    @Test
    fun signupMissingPrivacyReturnsFalse() {
        assertFalse(
            AuthLogic.canSubmit(AuthMode.SIGNUP, "hello@bohns.design", "abcdef", true, false, false),
        )
    }

    @Test
    fun signupAllFourConditionsMetReturnsTrue() {
        assertTrue(
            AuthLogic.canSubmit(AuthMode.SIGNUP, "hello@bohns.design", "abcdef", true, true, false),
        )
    }

    @Test
    fun isSubmittingTrueReturnsFalseInLogin() {
        assertFalse(
            AuthLogic.canSubmit(AuthMode.LOGIN, "hello@bohns.design", "abcdef", false, false, true),
        )
    }

    @Test
    fun isSubmittingTrueReturnsFalseInSignup() {
        assertFalse(
            AuthLogic.canSubmit(AuthMode.SIGNUP, "hello@bohns.design", "abcdef", true, true, true),
        )
    }

    @Test
    fun loginEmailMissingDotReturnsFalse() {
        assertFalse(
            AuthLogic.canSubmit(AuthMode.LOGIN, "hello@bohns", "abcdef", false, false, false),
        )
    }

    @Test
    fun loginEmailWithPlusReturnsFalse() {
        assertFalse(
            AuthLogic.canSubmit(AuthMode.LOGIN, "hello+work@bohns.design", "abcdef", false, false, false),
        )
    }

    @Test
    fun loginWhitespaceOnlyEmailReturnsFalse() {
        assertFalse(
            AuthLogic.canSubmit(AuthMode.LOGIN, "   ", "abcdef", false, false, false),
        )
    }

    @Test
    fun loginEmailWithSurroundingWhitespaceIsAccepted() {
        assertTrue(
            AuthLogic.canSubmit(AuthMode.LOGIN, "  hello@bohns.design  ", "abcdef", false, false, false),
        )
    }

    @Test
    fun normalizeEmailInputLowercasesUppercaseCharacters() {
        assertEquals("hello@bohns.design", AuthLogic.normalizeEmailInput("Hello@Bohns.Design"))
    }

    @Test
    fun normalizeEmailInputStripsPlusCharacters() {
        assertEquals("hellowork@bohns.design", AuthLogic.normalizeEmailInput("hello+work@bohns.design"))
    }

    @Test
    fun normalizeEmailInputIsIdempotentOnACleanAddress() {
        assertEquals("hello@bohns.design", AuthLogic.normalizeEmailInput("hello@bohns.design"))
    }
}
