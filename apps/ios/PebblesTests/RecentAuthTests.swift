import Foundation
import Supabase
import Testing
@testable import Pebbles

private func b64url(_ s: String) -> String {
    Data(s.utf8).base64EncodedString()
        .replacingOccurrences(of: "+", with: "-")
        .replacingOccurrences(of: "/", with: "_")
        .replacingOccurrences(of: "=", with: "")
}

/// The iOS half of the recent sign-in check (#977). Must agree with the SQL
/// `recent_auth_ok` (20260927120000), Android's RecentAuth and the web's
/// recent-auth.ts: any numeric amr `timestamp` within the window counts, and
/// anything unparseable does not.
@Suite("RecentAuth")
struct RecentAuthTests {
    private let now = Date(timeIntervalSince1970: 1_791_288_000) // 2026-10-06T12:00:00Z
    private var nowS: Int { Int(now.timeIntervalSince1970) }

    private static func jwt(_ payload: String) -> String {
        "\(b64url(#"{"alg":"HS256","typ":"JWT"}"#)).\(b64url(payload)).sig"
    }

    /// Real GoTrue payloads. The password `amr` array, `[{"method":"password",
    /// "timestamp":1790539740}]`, was captured verbatim from a real sign-up
    /// token by `verify-recent-auth.ts` against the linked project on
    /// 2026-09-27 (the capture Android and web pin too). Only the timestamp is
    /// substituted so the test controls the clock.
    private static func real(_ method: String, _ at: Int) -> String {
        #"{"aud":"authenticated","role":"authenticated","aal":"aal1","#
            + #""amr":[{"method":"\#(method)","timestamp":\#(at)}],"is_anonymous":false}"#
    }

    private static func realPassword(_ at: Int) -> String { real("password", at) }

    private static func realOauth(_ at: Int) -> String { real("oauth", at) }

    private func token(secondsAgo: Int, _ payload: (Int) -> String = realPassword) -> String {
        Self.jwt(payload(nowS - secondsAgo))
    }

    @Test("the verbatim captured token parses")
    func capturedToken() {
        let captured = Self.jwt(
            #"{"aud":"authenticated","role":"authenticated","aal":"aal1","#
                + #""amr":[{"method":"password","timestamp":1790539740}],"is_anonymous":false}"#
        )
        #expect(RecentAuth.newestStamp(accessToken: captured) == 1_790_539_740)
        #expect(RecentAuth.isFresh(accessToken: captured, now: Date(timeIntervalSince1970: 1_790_539_800)))
    }

    @Test("password and oauth sign-ins a minute ago are fresh")
    func freshSignIns() {
        #expect(RecentAuth.isFresh(accessToken: token(secondsAgo: 60), now: now))
        #expect(RecentAuth.isFresh(accessToken: token(secondsAgo: 60, Self.realOauth), now: now))
    }

    @Test("fresh exactly at the window edge, stale past it")
    func windowEdge() {
        #expect(RecentAuth.isFresh(accessToken: token(secondsAgo: 600), now: now))
        #expect(!RecentAuth.isFresh(accessToken: token(secondsAgo: 660), now: now))
    }

    @Test("any fresh stamp in a mixed amr counts")
    func mixed() {
        let token = Self.jwt(
            #"{"amr":[{"method":"password","timestamp":\#(nowS - 7200)},"#
                + #"{"method":"oauth","timestamp":\#(nowS - 30)}]}"#
        )
        #expect(RecentAuth.isFresh(accessToken: token, now: now))
    }

    @Test("missing, empty and malformed amr are never fresh", arguments: [
        "{}",
        #"{"amr":[]}"#,
        #"{"amr":null}"#,
        #"{"amr":{"method":"password","timestamp":1791288000}}"#,
        #"{"amr":[{"method":"password"}]}"#,
        #"{"amr":[{"method":"password","timestamp":"1791288000"}]}"#,
        #"{"amr":[{"method":"password","timestamp":true}]}"#,
        #"{"amr":[1,"x",null,{"timestamp":{"nested":1}}]}"#,
        #"{"amr":["pwd"]}"#
    ])
    func malformed(payload: String) {
        #expect(!RecentAuth.isFresh(accessToken: Self.jwt(payload), now: now))
    }

    @Test("non-JWTs are never fresh", arguments: [
        nil, "", "token", "a.b", "a.%%%.c", "a.\(b64url("not json")).c", "a.\(b64url("[1]")).c"
    ])
    func notJWT(token: String?) {
        #expect(!RecentAuth.isFresh(accessToken: token, now: now))
    }

    @Test("reauth_required is recognized in all three shapes, and nothing else is")
    func reauthRequired() {
        let body = Data(#"{"error":"reauth_required"}"#.utf8)
        #expect(RecentAuth.isReauthRequired(FunctionsError.httpError(code: 428, data: body)))
        #expect(RecentAuth.isReauthRequired(PostgrestError(code: "P0001", message: "reauth_required")))
        #expect(!RecentAuth.isReauthRequired(FunctionsError.httpError(code: 500, data: Data())))
        #expect(!RecentAuth.isReauthRequired(FunctionsError.httpError(code: 401, data: Data())))
        #expect(!RecentAuth.isReauthRequired(PostgrestError(code: "P0001", message: "handle_taken")))
        #expect(!RecentAuth.isReauthRequired(URLError(.notConnectedToInternet)))
    }

    @Test("GoTrue error codes map to reauth and wrong password")
    func authCodes() {
        let response = HTTPURLResponse(
            url: URL(string: "https://example.test")!, statusCode: 422, httpVersion: nil, headerFields: nil
        )!
        func api(_ code: ErrorCode) -> AuthError {
            .api(message: "", errorCode: code, underlyingData: Data(), underlyingResponse: response)
        }
        let needed = api(.reauthenticationNeeded)
        let wrong = api(.invalidCredentials)
        #expect(RecentAuth.isReauthRequired(needed))
        #expect(!RecentAuth.isReauthRequired(wrong))
        #expect(RecentAuth.isWrongPassword(wrong))
        #expect(!RecentAuth.isWrongPassword(needed))
        #expect(!RecentAuth.isWrongPassword(URLError(.timedOut)))
    }

    @Test("an email identity always means a password")
    func methodPassword() {
        #expect(ReauthMethod(providers: ["email"]) == .password)
        #expect(ReauthMethod(providers: ["google", "email"]) == .password)
        #expect(ReauthMethod(providers: []) == .password)
    }

    @Test("provider-only accounts re-run their provider")
    func methodProvider() {
        #expect(ReauthMethod(providers: ["google"]) == .google)
        #expect(ReauthMethod(providers: ["apple"]) == .apple)
        #expect(ReauthMethod(providers: ["apple", "google"]) == .google)
    }
}
