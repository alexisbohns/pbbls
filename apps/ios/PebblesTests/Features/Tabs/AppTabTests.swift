import Testing
@testable import Pebbles

/// #1314 D5: each tab draws a pebbles icon and the bar draws its `selected` form.
@Suite("AppTab icons")
struct AppTabTests {
    @Test("every tab has a distinct icon and a selected form that differs from regular (spec D6)")
    func icons() {
        let icons = AppTab.allCases.map(\.icon)
        #expect(Set(icons).count == AppTab.allCases.count)
        for tab in AppTab.allCases {
            #expect(tab.selectedForm != .regular, "\(tab) has no selected form")
            #expect(tab.icon.forms.contains(tab.selectedForm), "\(tab) selects a form its glyph lacks")
        }
        #expect(AppTab.home.icon == .home)
        #expect(AppTab.path.icon == .path)
        #expect(AppTab.people.icon == .people)
        #expect(AppTab.collections.icon == .collection)
        #expect(AppTab.you.icon == .person)
    }

    @Test("People selects its solid with the mark; the others follow the selected token")
    func selectedForms() {
        #expect(AppTab.people.selectedForm == GlyphForm(.solid, marked: true))
        for tab in AppTab.allCases where tab != .people {
            #expect(tab.selectedForm == tab.icon.form(for: .selected), "\(tab)")
        }
    }
}
