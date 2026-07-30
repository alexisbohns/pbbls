import Foundation
import Testing

@testable import Pebbles

/// `RootView.inviteToken(from:)` is the app's only URL entry point (M49,
/// design D11). It must accept exactly the canonical invite URL and reject
/// everything else — a loose parser would hand arbitrary strings to the
/// accept RPC.
@Suite("Invite link parsing")
struct InviteLinkTests {
    private let token = "abcDEF123-_xyz"

    @Test("accepts the canonical invite URL")
    func acceptsCanonical() {
        let url = URL(string: "https://www.pbbls.app/invite/\(token)")!
        #expect(RootView.inviteToken(from: url) == token)
    }

    @Test("rejects a different host")
    func rejectsForeignHost() {
        let url = URL(string: "https://evil.example.com/invite/\(token)")!
        #expect(RootView.inviteToken(from: url) == nil)
    }

    @Test("rejects plain http")
    func rejectsHTTP() {
        let url = URL(string: "http://www.pbbls.app/invite/\(token)")!
        #expect(RootView.inviteToken(from: url) == nil)
    }

    @Test("rejects the custom OAuth scheme")
    func rejectsCustomScheme() {
        let url = URL(string: "pebbles://auth-callback")!
        #expect(RootView.inviteToken(from: url) == nil)
    }

    @Test("rejects other paths on the same host")
    func rejectsOtherPaths() {
        for path in ["/docs/terms", "/invite", "/invite/", "/connections/\(token)"] {
            let url = URL(string: "https://www.pbbls.app\(path)")!
            #expect(RootView.inviteToken(from: url) == nil, "expected nil for \(path)")
        }
    }

    @Test("rejects a deeper path under /invite")
    func rejectsDeeperPath() {
        let url = URL(string: "https://www.pbbls.app/invite/\(token)/extra")!
        #expect(RootView.inviteToken(from: url) == nil)
    }
}
