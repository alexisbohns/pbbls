import Foundation
import Testing
@testable import Pebbles

/// #1146 D3: the ⋮ holds Edit moment, Edit pebble (pebbles only), then Delete
/// in the error colour, through stack A2's shared menu.
@Suite("HeldCardMenu")
@MainActor
struct HeldCardMenuTests {
    final class Calls: @unchecked Sendable { var names: [String] = [] }

    private func english(_ item: PebblesMenuItem) -> String {
        var copy = item.title
        copy.locale = Locale(identifier: "en")
        return String(localized: copy)
    }

    @Test("a pebble's menu: edit moment, edit pebble, an orange delete last")
    func pebble() {
        let calls = Calls()
        let items = HeldCardMenu.items(hasPebble: true, editMoment: { calls.names.append("moment") },
                                       editPebble: { calls.names.append("pebble") },
                                       delete: { calls.names.append("delete") })
        #expect(items.map(english) == ["Edit moment", "Edit pebble", "Delete"])
        #expect(items.map(\.isDestructive) == [false, false, true])
        items.forEach { $0.action() }
        #expect(calls.names == ["moment", "pebble", "delete"])
    }

    @Test("a bare moment's menu: edit moment and delete")
    func bare() {
        let items = HeldCardMenu.items(hasPebble: false, editMoment: {}, editPebble: {}, delete: {})
        #expect(items.map(english) == ["Edit moment", "Delete"])
    }
}
