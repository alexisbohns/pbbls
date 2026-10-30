import Testing
@testable import Pebbles

/// #1314 D5: each tab draws a pebbles icon and the bar lights its active twin.
@Suite("AppTab icons")
struct AppTabTests {
    @Test("every tab has a distinct icon with an active twin (spec D5)")
    func icons() {
        let icons = AppTab.allCases.map(\.icon)
        #expect(Set(icons).count == AppTab.allCases.count)
        for tab in AppTab.allCases {
            #expect(tab.icon.activeTwin != nil, "\(tab) has no active twin")
        }
        #expect(AppTab.home.icon == .home)
        #expect(AppTab.path.icon == .path)
        #expect(AppTab.people.icon == .people)
        #expect(AppTab.collections.icon == .collection)
        #expect(AppTab.you.icon == .person)
    }
}
