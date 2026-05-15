import Testing
@testable import Pebbles

@Suite("AuthView.canSubmit")
struct AuthViewLogicTests {
    @Test("login: invalid email returns false")
    func loginInvalidEmail() {
        #expect(AuthView.canSubmit(
            mode: .login, email: "not-an-email", password: "password",
            termsAccepted: false, privacyAccepted: false, isSubmitting: false
        ) == false)
    }

    @Test("login: short password returns false")
    func loginShortPassword() {
        #expect(AuthView.canSubmit(
            mode: .login, email: "hello@bohns.design", password: "abc",
            termsAccepted: false, privacyAccepted: false, isSubmitting: false
        ) == false)
    }

    @Test("login: valid credentials return true")
    func loginValid() {
        #expect(AuthView.canSubmit(
            mode: .login, email: "hello@bohns.design", password: "abcdef",
            termsAccepted: false, privacyAccepted: false, isSubmitting: false
        ) == true)
    }

    @Test("login: ignores consent flags")
    func loginIgnoresConsent() {
        #expect(AuthView.canSubmit(
            mode: .login, email: "hello@bohns.design", password: "abcdef",
            termsAccepted: true, privacyAccepted: true, isSubmitting: false
        ) == true)
    }

    @Test("signup: missing terms returns false")
    func signupMissingTerms() {
        #expect(AuthView.canSubmit(
            mode: .signup, email: "hello@bohns.design", password: "abcdef",
            termsAccepted: false, privacyAccepted: true, isSubmitting: false
        ) == false)
    }

    @Test("signup: missing privacy returns false")
    func signupMissingPrivacy() {
        #expect(AuthView.canSubmit(
            mode: .signup, email: "hello@bohns.design", password: "abcdef",
            termsAccepted: true, privacyAccepted: false, isSubmitting: false
        ) == false)
    }

    @Test("signup: all four conditions met returns true")
    func signupAllMet() {
        #expect(AuthView.canSubmit(
            mode: .signup, email: "hello@bohns.design", password: "abcdef",
            termsAccepted: true, privacyAccepted: true, isSubmitting: false
        ) == true)
    }

    @Test("isSubmitting=true returns false in login")
    func loginSubmitting() {
        #expect(AuthView.canSubmit(
            mode: .login, email: "hello@bohns.design", password: "abcdef",
            termsAccepted: false, privacyAccepted: false, isSubmitting: true
        ) == false)
    }

    @Test("isSubmitting=true returns false in signup")
    func signupSubmitting() {
        #expect(AuthView.canSubmit(
            mode: .signup, email: "hello@bohns.design", password: "abcdef",
            termsAccepted: true, privacyAccepted: true, isSubmitting: true
        ) == false)
    }

    @Test("login: email missing dot returns false")
    func loginEmailMissingDot() {
        #expect(AuthView.canSubmit(
            mode: .login, email: "hello@bohns", password: "abcdef",
            termsAccepted: false, privacyAccepted: false, isSubmitting: false
        ) == false)
    }

    @Test("login: email with '+' returns false")
    func loginEmailWithPlus() {
        #expect(AuthView.canSubmit(
            mode: .login, email: "hello+work@bohns.design", password: "abcdef",
            termsAccepted: false, privacyAccepted: false, isSubmitting: false
        ) == false)
    }

    @Test("login: whitespace-only email returns false")
    func loginWhitespaceEmail() {
        #expect(AuthView.canSubmit(
            mode: .login, email: "   ", password: "abcdef",
            termsAccepted: false, privacyAccepted: false, isSubmitting: false
        ) == false)
    }

    @Test("login: email with surrounding whitespace is accepted")
    func loginEmailTrimmed() {
        #expect(AuthView.canSubmit(
            mode: .login, email: "  hello@bohns.design  ", password: "abcdef",
            termsAccepted: false, privacyAccepted: false, isSubmitting: false
        ) == true)
    }

    @Test("normalizeEmailInput lowercases uppercase characters")
    func normalizeLowercases() {
        #expect(AuthView.normalizeEmailInput("Hello@Bohns.Design") == "hello@bohns.design")
    }

    @Test("normalizeEmailInput strips '+' characters")
    func normalizeStripsPlus() {
        #expect(AuthView.normalizeEmailInput("hello+work@bohns.design") == "hellowork@bohns.design")
    }

    @Test("normalizeEmailInput is idempotent on a clean address")
    func normalizeIdempotent() {
        #expect(AuthView.normalizeEmailInput("hello@bohns.design") == "hello@bohns.design")
    }
}
