import SwiftUI
import UIKit

/// The snap-present heading of the read view (issue #599): the whole picture at
/// its nearest `BannerAspect` bucket, tilted slightly, with the
/// `PebbleReadPetroglyph` overlapping its top-right corner and tilted the other
/// way. Ports the web `PebbleDetail` snap+pebble bundle.
///
/// Stateless — the caller resolves the decoded `image` and its `aspect`, so the
/// same frame can back the edit view's dashed placeholder in the #599 follow-up.
/// The photo is inset from the cluster's top and trailing edges (the poke
/// insets) so the top-trailing Petroglyph pokes out past the corner while
/// overlapping the picture; the tilts are draw-only (`rotationEffect` does not
/// affect layout), so each rounded rectangle turns in place. The snap is hidden
/// from accessibility (web/Android parity).
struct PebbleSnapFrame: View {
    let image: UIImage
    let aspect: BannerAspect
    let renderSvg: String?
    let renderVersion: String?
    let valence: Valence
    let palette: EmotionPalette?

    private static let photoMinor: CGFloat = 150
    /// Longer photo edge for 4:3 / 3:4 snaps (150 × 4/3).
    private static let photoMajor: CGFloat = 200
    /// Top inset of the photo — how far the Petroglyph pokes above it.
    private static let pokeTop: CGFloat = 40
    /// Trailing inset of the photo — how far the Petroglyph pokes past its edge.
    private static let pokeEnd: CGFloat = 28
    /// Layout slot for the overlapping Petroglyph (its tilt bounding runs a touch larger).
    private static let overlayPetroglyph: CGFloat = 96
    private static let photoCorner: CGFloat = 18
    /// Counter-clockwise photo tilt, clockwise Petroglyph tilt — the web `-rotate-4` / `rotate-7`.
    private static let photoTilt: Double = -5
    private static let petroglyphTilt: Double = 7.5

    var body: some View {
        // The cluster (ZStack) wraps the photo plus its poke insets; the
        // Petroglyph aligns to the cluster's top-trailing so it lands on the
        // photo's corner. The insets are asymmetric — it pokes above the photo
        // more than past its trailing edge, matching the design.
        ZStack(alignment: .topTrailing) {
            Image(uiImage: image)
                .resizable()
                .aspectRatio(contentMode: .fill)
                .frame(width: photoSize.width, height: photoSize.height)
                .clipShape(RoundedRectangle(cornerRadius: Self.photoCorner))
                .rotationEffect(.degrees(Self.photoTilt))
                .padding(.top, Self.pokeTop)
                .padding(.trailing, Self.pokeEnd)
                .accessibilityHidden(true)

            PebbleReadPetroglyph(
                renderSvg: renderSvg,
                renderVersion: renderVersion,
                valence: valence,
                palette: palette
            )
            .frame(width: Self.overlayPetroglyph, height: Self.overlayPetroglyph)
            .rotationEffect(.degrees(Self.petroglyphTilt))
        }
        .frame(maxWidth: .infinity)
    }

    /// Photo box for the bucket: square keeps both edges short; landscape and
    /// portrait stretch one edge to `photoMajor`.
    private var photoSize: CGSize {
        switch aspect {
        case .square:    return CGSize(width: Self.photoMinor, height: Self.photoMinor)
        case .fourThree: return CGSize(width: Self.photoMajor, height: Self.photoMinor)
        case .threeFour: return CGSize(width: Self.photoMinor, height: Self.photoMajor)
        }
    }
}

#if DEBUG
/// A neutral fill stands in for the decoded snap — a preview cannot reach
/// Supabase Storage, so a real rendition can't load here.
private func previewSnapImage() -> UIImage {
    UIGraphicsImageRenderer(size: CGSize(width: 4, height: 4)).image { ctx in
        UIColor(red: 0.42, green: 0.48, blue: 0.56, alpha: 1).setFill()
        ctx.fill(CGRect(x: 0, y: 0, width: 4, height: 4))
    }
}

private let previewSnapPalette = EmotionPalette(
    primaryHex: "#7B5E99FF",
    secondaryHex: "#AE91CCFF",
    lightHex: "#F2EFF5FF",
    surfaceHex: "#7B5E991A",
    darkHex: "#2A2138FF",
    shadedHex: "#4A3A5CFF"
)

private let previewSnapSvg = """
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
  <circle cx="50" cy="50" r="40" fill="none" stroke="currentColor" stroke-width="3"/>
</svg>
"""

#Preview("Snap frame · square") {
    PebbleSnapFrame(
        image: previewSnapImage(),
        aspect: .square,
        renderSvg: previewSnapSvg,
        renderVersion: "0.1.0",
        valence: .neutralMedium,
        palette: previewSnapPalette
    )
    .padding(.vertical, 24)
    .frame(maxWidth: .infinity)
    .background(Color.system.background)
}

#Preview("Snap frame · landscape") {
    PebbleSnapFrame(
        image: previewSnapImage(),
        aspect: .fourThree,
        renderSvg: previewSnapSvg,
        renderVersion: "0.1.0",
        valence: .neutralMedium,
        palette: previewSnapPalette
    )
    .padding(.vertical, 24)
    .frame(maxWidth: .infinity)
    .background(Color.system.background)
}

#Preview("Snap frame · portrait large") {
    PebbleSnapFrame(
        image: previewSnapImage(),
        aspect: .threeFour,
        renderSvg: previewSnapSvg,
        renderVersion: "0.1.0",
        valence: .highlightLarge,
        palette: previewSnapPalette
    )
    .padding(.vertical, 24)
    .frame(maxWidth: .infinity)
    .background(Color.system.background)
}
#endif
