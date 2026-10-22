import SwiftUI

/// The one place the held card is presented (#1146 D1–D2), applied once to
/// `PebblesTabView` in `RootView`, so one presentation serves every tab. In
/// E1–E3 it is a `.large` sheet; a swipe down closes the card as Back does.
/// E4 replaces it with the full-screen layer and its flight.
struct HeldCardHost: ViewModifier {
    let host: HeldCardHostModel

    func body(content: Content) -> some View {
        content.sheet(item: Binding(get: { host.request }, set: { if $0 == nil { host.close() } })) { request in
            PebbleDetailSheet(request: request, onClose: { host.close() })
        }
    }
}

extension View {
    func heldCardHost(_ host: HeldCardHostModel) -> some View {
        modifier(HeldCardHost(host: host))
    }
}
