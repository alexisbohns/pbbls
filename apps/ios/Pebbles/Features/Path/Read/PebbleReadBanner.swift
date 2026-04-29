import SwiftUI

/// Top zone of the pebble read view.
///
/// With a photo: renders a 16:9 cover banner with rounded corners; the
/// pebble shape sits in a 120×120pt page-bg-fill box centered over the
/// banner's bottom edge (50% over photo, 50% below).
///
/// Without a photo: renders only the pebble centered in a 120pt-tall zone
/// — no box, no banner. Vertical footprint matches the with-photo case so
/// the layout below stays consistent.
struct PebbleReadBanner: View {
    let snapStoragePath: String?
    let renderSvg: String?
    let emotionColorHex: String
    let valence: Valence

    private let bannerCornerRadius: CGFloat = 24
    private let boxSize: CGFloat = 120
    private let boxCornerRadius: CGFloat = 24

    var body: some View {
        if let snapStoragePath {
            withPhoto(storagePath: snapStoragePath)
        } else {
            withoutPhoto
        }
    }

    @ViewBuilder
    private func withPhoto(storagePath: String) -> some View {
        VStack(spacing: 0) {
            SnapImageView(storagePath: storagePath, contentMode: .fill)
                .aspectRatio(16.0 / 9.0, contentMode: .fit)
                .clipShape(RoundedRectangle(cornerRadius: bannerCornerRadius))
                .accessibilityHidden(true)
                .overlay(alignment: .bottom) {
                    pebbleBox
                        .offset(y: boxSize / 2)
                }
                .padding(.bottom, boxSize / 2)
        }
        .frame(maxWidth: .infinity)
    }

    private var withoutPhoto: some View {
        VStack {
            renderedPebble
        }
        .frame(maxWidth: .infinity, minHeight: boxSize)
    }

    private var pebbleBox: some View {
        renderedPebble
            .frame(width: boxSize, height: boxSize)
            .background(
                RoundedRectangle(cornerRadius: boxCornerRadius)
                    .fill(Color.pebblesBackground)
            )
    }

    @ViewBuilder
    private var renderedPebble: some View {
        if let renderSvg {
            PebbleRenderView(svg: renderSvg, strokeColor: emotionColorHex)
                .frame(height: pebbleHeight)
        } else {
            EmptyView()
        }
    }

    /// Pebble height inside the 120pt box, scaled by valence so high
    /// intensity reads bigger than low intensity but always fits comfortably.
    private var pebbleHeight: CGFloat {
        switch valence.sizeGroup {
        case .small:  return 80
        case .medium: return 100
        case .large:  return 116
        }
    }
}

#Preview("With photo · medium") {
    PebbleReadBanner(
        snapStoragePath: nil, // preview without network — see no-photo preview
        renderSvg: """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
              <circle cx="50" cy="50" r="40" fill="none" stroke="currentColor" stroke-width="3"/>
            </svg>
            """,
        emotionColorHex: "#7C5CFA",
        valence: .neutralMedium
    )
    .padding()
    .background(Color.pebblesBackground)
}

#Preview("Without photo · large") {
    PebbleReadBanner(
        snapStoragePath: nil,
        renderSvg: """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
              <circle cx="50" cy="50" r="40" fill="none" stroke="currentColor" stroke-width="3"/>
            </svg>
            """,
        emotionColorHex: "#7C5CFA",
        valence: .highlightLarge
    )
    .padding()
    .background(Color.pebblesBackground)
}
