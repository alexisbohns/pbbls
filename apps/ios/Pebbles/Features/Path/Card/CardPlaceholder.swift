import SwiftUI

/// The blank card a fading open shows while the moment loads (#1146 §Flight,
/// first frame): the stock at 1 : 1.3 for up to 400 ms, then a progress
/// indicator in it. A seeded open shows the seed's recto instead.
struct CardPlaceholder: View {
    let width: CGFloat

    @State private var waited = false

    var body: some View {
        RoundedRectangle(cornerRadius: 11, style: .continuous)
            .fill(Color.pebbles.stock)
            .frame(width: width, height: width * 1.3)
            .overlay {
                if waited { ProgressView() }
            }
            .task {
                try? await Task.sleep(for: .milliseconds(400))
                waited = true
            }
    }
}
