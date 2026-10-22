import SwiftUI

/// E1–E3 interim (#1146 D2): the held card's page in a `.large` sheet, which
/// `HeldCardHost` presents for every caller. The page needs the whole height,
/// which E4's overlay gives it; E5 deletes this file.
struct PebbleDetailSheet: View {
    let request: HeldCardRequest
    let onClose: () -> Void

    var body: some View {
        HeldCardPage(request: request, onClose: onClose)
            .presentationDetents([.large])
    }
}
