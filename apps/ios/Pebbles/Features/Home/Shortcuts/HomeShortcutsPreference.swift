import Foundation
import SwiftUI

/// How the Home shortcuts are drawn, kept on this device and switched in
/// Settings: a row of three or a bento, plain tiles or pebble shapes. The
/// pebble-shaped row is the default. The bento's square goes to the shortcut
/// used last.
enum HomeShortcutsPreference {
    static let layoutKey = "home.shortcuts.layout"
    static let stonesKey = "home.shortcuts.stones"
    static let lastUsedKey = "home.shortcuts.lastUsed"
}

enum HomeShortcutsLayout: String, CaseIterable, Identifiable {
    case row
    case bento

    var id: Self { self }

    var title: LocalizedStringResource {
        switch self {
        case .row: "Row"
        case .bento: "Bento"
        }
    }
}

/// The three Home shortcuts, in their fixed order (spec D3). The raw values
/// are stored as the last used one: never rename them.
enum HomeShortcut: String, CaseIterable {
    case camera
    case quick
    case pebble

    var icon: PbblsIcon {
        switch self {
        case .camera: .camera
        case .quick: .pebbleFlat
        case .pebble: .plusCircle
        }
    }

    var title: LocalizedStringKey {
        switch self {
        case .camera: "home.shortcut.camera"
        case .quick: "home.shortcut.quick"
        case .pebble: "home.shortcut.pebble"
        }
    }

    /// The bento's order: `lead` takes the square, the other two stack beside
    /// it in the fixed order.
    static func bento(lead: HomeShortcut) -> [HomeShortcut] {
        [lead] + allCases.filter { $0 != lead }
    }
}
