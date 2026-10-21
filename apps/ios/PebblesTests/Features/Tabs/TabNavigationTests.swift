import Foundation
import Testing
@testable import Pebbles

/// #903 §4: back is per stack; a reselect pops first, then scrolls.
@Suite("TabNavigation — selection")
@MainActor
struct TabNavigationTests {
    private let soulId = UUID(uuidString: "11111111-1111-1111-1111-111111111111")!
    private let collectionId = UUID(uuidString: "22222222-2222-2222-2222-222222222222")!

    @Test("starts on Path with every stack empty and no scroll requests")
    func defaults() {
        let nav = TabNavigation()
        #expect(nav.selected == .path)
        #expect(nav.path.isEmpty)
        #expect(nav.people.isEmpty)
        #expect(nav.collections.isEmpty)
        #expect(nav.you.isEmpty)
        #expect(nav.scrollToTop.isEmpty)
    }

    @Test("selecting another tab switches without touching any stack")
    func selectSwitches() {
        let nav = TabNavigation()
        nav.people = [.soul(soulId)]
        nav.select(.you)
        #expect(nav.selected == .you)
        #expect(nav.people == [.soul(soulId)])
        #expect(nav.scrollToTop.isEmpty)
    }

    @Test("reselecting a pushed tab pops it to root and does not scroll")
    func reselectPops() {
        let nav = TabNavigation()
        nav.select(.people)
        nav.people = [.soul(soulId), .connections]
        nav.select(.people)
        #expect(nav.selected == .people)
        #expect(nav.people.isEmpty)
        #expect(nav.scrollToTop[.people] == nil)
    }

    @Test("reselecting a tab at its root bumps only that tab's scroll token")
    func reselectAtRootScrolls() {
        let nav = TabNavigation()
        nav.select(.collections)
        nav.select(.collections)
        nav.select(.collections)
        #expect(nav.scrollToTop[.collections] == 2)
        #expect(nav.scrollToTop[.people] == nil)
        #expect(nav.scrollToTop[.path] == nil)
    }

    @Test("pop then scroll takes two taps (D3)")
    func popThenScroll() {
        let nav = TabNavigation()
        nav.select(.you)
        nav.you = [.lab]
        nav.select(.you)
        #expect(nav.you.isEmpty)
        #expect(nav.scrollToTop[.you] == nil)
        nav.select(.you)
        #expect(nav.scrollToTop[.you] == 1)
    }

    @Test("reselecting Path at root asks Path to scroll, since Path never pushes")
    func pathReselect() {
        let nav = TabNavigation()
        nav.select(.path)
        #expect(nav.scrollToTop[.path] == 1)
    }

    @Test("popToRoot leaves the other stacks intact")
    func popToRootIsLocal() {
        let nav = TabNavigation()
        nav.people = [.soul(soulId)]
        nav.collections = [.collection(collectionId)]
        nav.you = [.glyphs]
        nav.popToRoot(.collections)
        #expect(nav.collections.isEmpty)
        #expect(nav.people == [.soul(soulId)])
        #expect(nav.you == [.glyphs])
    }

    @Test("depth reports each stack's own count")
    func depth() {
        let nav = TabNavigation()
        nav.people = [.soul(soulId), .connections]
        #expect(nav.depth(of: .people) == 2)
        #expect(nav.depth(of: .you) == 0)
        #expect(nav.depth(of: .path) == 0)
    }
}
