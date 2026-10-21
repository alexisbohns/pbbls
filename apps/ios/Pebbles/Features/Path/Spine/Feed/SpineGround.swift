import SwiftUI

/// The raked ground behind a week's days (#1130 D6, R11). One background of the
/// days block, so the tile's phase is the content's own y: no heights are
/// summed and a far jump cannot re-phase it (Android's lesson). Masked full to
/// `colX − 6`, then down to 22 % by `colX + 110`. The line (1 pt at x 40, in
/// `Color.pebbles.line`) runs over it from the first hairline to the bottom;
/// the mouth carries its last 26 pt. No grain (D7).
struct SpineGround: View {
    @Environment(\.colorScheme) private var scheme
    @Environment(\.displayScale) private var displayScale

    var body: some View {
        GeometryReader { proxy in
            let width = max(proxy.size.width, 1)
            ZStack(alignment: .topLeading) {
                if let tile = SpineRakeTile.image(scheme: scheme, scale: displayScale) {
                    Image(uiImage: tile)
                        .resizable(resizingMode: .tile)
                        .mask(mask(width: width))
                }
                Rectangle()
                    .fill(Color.pebbles.line)
                    .frame(width: SpineLayout.line,
                           height: max(0, proxy.size.height - SpineLayout.seamOverhang))
                    .offset(x: SpineLayout.spineX - SpineLayout.line / 2, y: SpineLayout.seamOverhang)
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    /// Alpha only: any opaque role draws the mask.
    private func mask(width: CGFloat) -> LinearGradient {
        let opaque = Color.pebbles.onSurface
        return LinearGradient(stops: [
            .init(color: opaque, location: 0),
            .init(color: opaque, location: min(1, (SpineLayout.colX - 6) / width)),
            .init(color: opaque.opacity(0.22), location: min(1, (SpineLayout.colX + 110) / width))
        ], startPoint: .leading, endPoint: .trailing)
    }
}
