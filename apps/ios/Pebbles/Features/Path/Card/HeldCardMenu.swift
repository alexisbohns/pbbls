import Foundation

/// The held card's ⋮ (#1146 D3): stack A2's rule, so the items go through the
/// shared `PebblesMenu` and Delete is in `Color.pebbles.error` (orange, never
/// red). The shared list draws a rule between rows, the divider before Delete
/// included (decision 5). The card adds no menu of its own.
enum HeldCardMenu {
    static func items(hasPebble: Bool, editMoment: @escaping () -> Void, editPebble: @escaping () -> Void,
                      delete: @escaping () -> Void) -> [PebblesMenuItem] {
        var items = [PebblesMenuItem(title: "Edit moment", systemImage: "pencil", action: editMoment)]
        if hasPebble {
            items.append(PebblesMenuItem(title: "Edit pebble", systemImage: "circle.hexagongrid", action: editPebble))
        }
        items.append(.delete(delete))
        return items
    }
}
