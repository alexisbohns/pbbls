import Foundation
import Testing
@testable import Pebbles

/// #903 §4: back is per stack; a reselect pops first, then scrolls.
@Suite("TabNavigation — selection")
@MainActor
struct TabNavigationTests {
    private let soulId = UUID(uuidString: "11111111-1111-1111-1111-111111111111")!
    private let collectionId = UUID(uuidString: "22222222-2222-2222-2222-222222222222")!

    @Test("starts on Home with every stack empty and no scroll requests")
    func defaults() {
        let nav = TabNavigation()
        #expect(nav.selected == .home)
        #expect(nav.home.isEmpty)
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

@Suite("TabNavigation — home")
@MainActor
struct TabNavigationHomeTests {
    @Test func homeIsFirstInBarOrderAndTheDefaultSelection() {
        #expect(AppTab.allCases.first == .home)
        #expect(TabNavigation().selected == .home)
    }

    @Test func anOldSnapshotWithoutHomeStillRestores() throws {
        // Written before the Home tab existed: no `home` field.
        let old = #"{"selected":"people","path":[],"people":[],"collections":[],"you":[]}"#
        let snapshot = try #require(TabNavigation.Snapshot.decoded(from: Data(old.utf8)))
        let nav = TabNavigation()
        nav.restore(snapshot)
        #expect(nav.selected == .people)
        #expect(nav.home.isEmpty)
    }

    @Test func homeRouteRoundTrips() throws {
        let nav = TabNavigation()
        nav.select(.home)
        nav.home = [.guide("stones")]
        let data = try #require(nav.snapshot.encoded)
        let restored = try #require(TabNavigation.Snapshot.decoded(from: data))
        #expect(restored.home == [.guide("stones")])
    }

    @Test func anIntentIsConsumedOnce() {
        let nav = TabNavigation()
        nav.go(.people, intent: .createSoul)
        #expect(nav.selected == .people)
        #expect(nav.takeIntent(for: .people) == .createSoul)
        #expect(nav.takeIntent(for: .people) == nil)
        #expect(nav.takeIntent(for: .collections) == nil)
    }
}

/// #903 §3 / §11: restoration survives a system kill, and a snapshot from an
/// older build never crashes the launch.
@Suite("TabNavigation — snapshot")
@MainActor
struct TabNavigationSnapshotTests {
    private let soulId = UUID(uuidString: "11111111-1111-1111-1111-111111111111")!
    private let collectionId = UUID(uuidString: "22222222-2222-2222-2222-222222222222")!

    @Test("snapshot and restore round-trip every stack and the selection")
    func roundTrip() throws {
        let nav = TabNavigation()
        nav.select(.collections)
        nav.people = [.soul(soulId), .connections]
        nav.collections = [.collection(collectionId)]
        nav.you = [.glyphs, .achievements, .lab]

        let data = try #require(nav.snapshot.encoded)
        let restored = TabNavigation()
        restored.restore(try #require(TabNavigation.Snapshot.decoded(from: data)))

        #expect(restored.snapshot == nav.snapshot)
        #expect(restored.selected == .collections)
        #expect(restored.people == [.soul(soulId), .connections])
        #expect(restored.collections == [.collection(collectionId)])
        #expect(restored.you == [.glyphs, .achievements, .lab])
    }

    @Test("an unknown tab raw value decodes to Path and keeps the stacks")
    func unknownTab() throws {
        let json = #"{"selected":"settings","path":[],"people":[{"connections":{}}],"collections":[],"you":[]}"#
        let snapshot = try #require(TabNavigation.Snapshot.decoded(from: Data(json.utf8)))
        #expect(snapshot.selected == .path)
        #expect(snapshot.people == [.connections])
    }

    @Test("a removed route case drops that stack to root, not the whole snapshot")
    func removedRouteCase() throws {
        let json = #"{"selected":"you","path":[],"people":[{"connections":{}}],"collections":[],"you":[{"wallet":{}}]}"#
        let snapshot = try #require(TabNavigation.Snapshot.decoded(from: Data(json.utf8)))
        #expect(snapshot.selected == .you)
        #expect(snapshot.you.isEmpty)
        #expect(snapshot.people == [.connections])
    }

    @Test("missing keys decode to defaults")
    func missingKeys() throws {
        let snapshot = try #require(TabNavigation.Snapshot.decoded(from: Data("{}".utf8)))
        #expect(snapshot == TabNavigation.Snapshot(selected: .path))
    }

    @Test("a wrong-typed field falls back to its own default and keeps the rest")
    func wrongTypedFields() throws {
        let json = #"{"selected":3,"path":[],"people":"x","collections":[],"you":[{"lab":{}}]}"#
        let snapshot = try #require(TabNavigation.Snapshot.decoded(from: Data(json.utf8)))
        #expect(snapshot.selected == .path)
        #expect(snapshot.people.isEmpty)
        #expect(snapshot.you == [.lab])
    }

    @Test("a top-level JSON array is not a snapshot and decodes to nil")
    func topLevelArray() {
        #expect(TabNavigation.Snapshot.decoded(from: Data("[]".utf8)) == nil)
    }

    @Test("bytes that are not a snapshot decode to nil")
    func garbage() {
        #expect(TabNavigation.Snapshot.decoded(from: Data("not json".utf8)) == nil)
    }

    @Test("reset empties every stack, the selection and the scroll tokens")
    func reset() {
        let nav = TabNavigation()
        nav.select(.people)
        nav.people = [.soul(soulId)]
        nav.you = [.lab]
        nav.select(.path)
        nav.select(.path)
        nav.reset()
        #expect(nav.snapshot == TabNavigation.Snapshot())
        #expect(nav.scrollToTop.isEmpty)
    }
}
