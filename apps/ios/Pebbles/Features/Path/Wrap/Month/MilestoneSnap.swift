import os
import SwiftUI
import UIKit

/// A milestone's photo framed as a small card (#1241 D16): the stock under a
/// rule, the photo cropped to its window, a blank strip under it like the
/// pebble card's, a slight tilt. It loads as the Spine's card face does: the
/// snap source's image, the shared cache's, or its own fetch of the signed
/// original; until then the window is a bed in the pebble's tint. Decorative:
/// the row says the milestone.
struct MilestoneSnap: View {
    let path: String
    /// The bed's ink while the photo loads.
    let tint: Color
    let height: CGFloat

    @Environment(\.spineSnapSource) private var snaps
    @State private var fetched: (path: String, image: UIImage)?

    /// Width over height: a pebble card's portrait.
    static let aspect: CGFloat = 0.78
    /// How much of the card's inner height the window takes; the rest is the strip.
    private static let window: CGFloat = 0.76
    private static let tilt: Double = -4
    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "wrap")

    var body: some View {
        let width = height * Self.aspect
        let inset = max(2, height * 0.06)
        let shape = RoundedRectangle(cornerRadius: max(2, height * 0.05), style: .continuous)
        VStack(spacing: 0) {
            photo
                .frame(width: width - 2 * inset, height: (height - 2 * inset) * Self.window)
                .clipped()
            Spacer(minLength: 0)
        }
        .padding(inset)
        .frame(width: width, height: height)
        .background { shape.fill(Color.pebbles.stock) }
        .overlay { shape.strokeBorder(Color.pebbles.cardRule, lineWidth: SpineLayout.rule) }
        .rotationEffect(.degrees(Self.tilt))
        .task(id: path) { await load() }
        .accessibilityHidden(true)
    }

    @ViewBuilder
    private var photo: some View {
        if let image = decoded {
            Image(uiImage: image).resizable().scaledToFill()
        } else {
            Rectangle().fill(tint.opacity(0.18))
        }
    }

    private var decoded: UIImage? {
        snaps.image(path) ?? SnapImageCache.shared.image(for: path) ?? (fetched?.path == path ? fetched?.image : nil)
    }

    /// Fetches the signed original once and keeps the decoded image in the shared cache, as the Spine's card does.
    private func load() async {
        guard decoded == nil else { return }
        // Unsigned: the cache logged why; the bed stays.
        guard let url = await snaps.url(path) else { return }
        do {
            let (data, _) = try await URLSession.shared.data(from: url)
            guard let image = await UIImage(data: data)?.byPreparingForDisplay() else {
                throw URLError(.cannotDecodeContentData)
            }
            SnapImageCache.shared.store(image, for: path)
            guard !Task.isCancelled else { return }
            fetched = (path, image)
        } catch {
            guard !Task.isCancelled else { return }
            Self.logger.warning("milestone photo failed: \(error.localizedDescription, privacy: .private)")
        }
    }
}
