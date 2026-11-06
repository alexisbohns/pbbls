import Testing
import UIKit
@testable import Pebbles

/// The overlay window above the app: empty areas pass touches through, and the
/// unlock moment holds them all. SwiftUI content hits the hosting root itself, so
/// the moment cannot be told from an empty area by the view that was hit.
@Suite("Karma passthrough window")
@MainActor
struct KarmaPassthroughWindowTests {
    private let centre = CGPoint(x: 50, y: 50)

    private func makeWindow() -> KarmaPassthroughWindow {
        let window = KarmaPassthroughWindow(frame: CGRect(x: 0, y: 0, width: 100, height: 100))
        let root = UIViewController()
        window.rootViewController = root
        root.view.frame = window.bounds
        window.isHidden = false
        return window
    }

    @Test("an empty area passes the touch through")
    func emptyAreaPassesThrough() {
        #expect(makeWindow().hitTest(centre, with: nil) == nil)
    }

    @Test("while the moment is up, the root takes the touch")
    func momentHoldsTheTouch() {
        let window = makeWindow()
        window.capturesTouches = { true }
        #expect(window.hitTest(centre, with: nil) === window.rootViewController?.view)
    }

    @Test("once the moment is gone, touches pass through again")
    func momentGoneReleases() {
        let window = makeWindow()
        var isUp = true
        window.capturesTouches = { isUp }
        #expect(window.hitTest(centre, with: nil) != nil)
        isUp = false
        #expect(window.hitTest(centre, with: nil) == nil)
    }

    @Test("a real subview takes the touch without the moment")
    func subviewTakesTheTouch() {
        let window = makeWindow()
        let subview = UIView(frame: window.bounds)
        window.rootViewController?.view.addSubview(subview)
        #expect(window.hitTest(centre, with: nil) === subview)
    }
}
