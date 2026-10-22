import SwiftUI

/// Three stone-shaped waves across the ground (#1130 R12, R14). `SpineStone`
/// mounts it only while a press is live, and it is gone after its last frame.
/// The path is scaled, not the canvas, so every wave stays a 1 pt hairline
/// however far it travels (the web's non-scaling stroke).
struct StoneRipple: View {
    let geometry: StoneRingGeometry
    let stone: CGSize
    let waves: [StoneMotion.WaveSample]
    /// How far the first wave travels, in stone widths.
    let reach: Double

    var body: some View {
        let side = CGSize(width: stone.width * (reach + 1), height: stone.height * (reach + 1))
        let origin = CGPoint(x: (side.width - stone.width) / 2, y: (side.height - stone.height) / 2)
        Canvas { context, _ in
            for wave in waves where wave.alpha > 0 {
                let path = geometry.path(scaled: CGSize(width: wave.scale, height: wave.scale), offset: origin)
                context.stroke(path, with: .color(Color.pebbles.ripple.opacity(wave.alpha)), lineWidth: 1)
            }
        }
        .frame(width: side.width, height: side.height)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}
