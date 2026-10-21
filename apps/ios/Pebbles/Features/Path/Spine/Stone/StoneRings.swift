import SwiftUI

extension StoneRingGeometry {
    /// The outline scaled by `scale` about the centroid, then moved by `offset`
    /// (the stone's origin inside a larger canvas).
    func path(scaled scale: CGSize, offset: CGPoint) -> Path {
        let transform = CGAffineTransform(translationX: offset.x + centroid.x, y: offset.y + centroid.y)
            .scaledBy(x: scale.width, y: scale.height)
            .translatedBy(x: -centroid.x, y: -centroid.y)
        return outline.applying(transform)
    }
}

/// The halo (the clearing, `Color.pebbles.halo`) and the raked rings (#1130 R3).
/// A press swells both together (R12); under Reduce Motion the rings take ink-2
/// instead (R13). Drawn on a canvas twice the stone's size, centred on it and
/// never clipped, so the halo spills onto the ground.
struct StoneRings: View {
    let geometry: StoneRingGeometry
    let stone: CGSize
    let scale: Double
    let still: Bool

    var body: some View {
        let canvas = CGSize(width: stone.width * 2, height: stone.height * 2)
        let origin = CGPoint(x: stone.width / 2, y: stone.height / 2)
        let ink = still ? Color.pebbles.onSurfaceVariant : Color.pebbles.ring
        Canvas { context, _ in
            context.fill(geometry.path(scaled: geometry.halo, offset: origin), with: .color(Color.pebbles.halo))
            for ring in geometry.rings {
                context.stroke(geometry.path(scaled: ring, offset: origin), with: .color(ink), lineWidth: 1)
            }
        }
        .frame(width: canvas.width, height: canvas.height)
        .scaleEffect(scale, anchor: UnitPoint(x: (origin.x + geometry.centroid.x) / canvas.width,
                                              y: (origin.y + geometry.centroid.y) / canvas.height))
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}
