import SwiftUI

/// One stone as a shape (#1382 D7): the chips, the settings button and the
/// community callout. Flat: the caller fills it, nothing borders or shadows it.
/// The same seed draws the same stone at the same size.
struct StoneShape: Shape {
    let seed: String
    var options = StoneOptions()

    func path(in rect: CGRect) -> Path {
        StoneGeometry.path(StoneCache.single(size: rect.size, seed: seed, options: options))
            .offsetBy(dx: rect.minX, dy: rect.minY)
    }
}
