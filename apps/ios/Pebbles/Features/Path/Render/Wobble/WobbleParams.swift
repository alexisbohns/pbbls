import CoreGraphics

/// Canonical wobble parameters from issue #555 §1, plus the §2.1 rule mapping
/// them into an arbitrary coordinate space. All values are authored for a
/// normalized 200-unit box; `scaled(for:)` preserves the visual density when
/// the geometry lives in a different space (pebble canvases, backdrop assets).
struct WobbleParams {
    /// Max displacement, in the geometry's own units.
    var amplitude: Double
    /// Noise base frequency, in the geometry's own units.
    var frequency: Double
    /// Fractal octave count.
    var octaves: Int
    /// Target chord length when flattening, in the geometry's own units.
    var flattenStep: Double

    /// The approved look (issue #555 §1) for geometry in the 200-box.
    static let canonical = WobbleParams(amplitude: 18, frequency: 0.024, octaves: 5, flattenStep: 2)

    /// The static look's single seed; boil variants would use seed + k later.
    static let seed = 3

    /// §2.1: for a w×h space, with s = 200 / max(w, h), amplitude and step
    /// scale by 1/s and frequency by s — equivalent to normalizing into the
    /// 200-box, wobbling there, and scaling back.
    static func scaled(for spaceSize: CGSize) -> WobbleParams {
        let longestSide = Double(max(spaceSize.width, spaceSize.height))
        guard longestSide > 0 else { return .canonical }
        let normalization = 200 / longestSide
        return WobbleParams(
            amplitude: canonical.amplitude / normalization,
            frequency: canonical.frequency * normalization,
            octaves: canonical.octaves,
            flattenStep: canonical.flattenStep / normalization
        )
    }

    /// Displaces one point with the R/G noise channels.
    ///
    /// The sign is MINUS: feDisplacementMap samples source pixels at
    /// p + scale·(noise − 0.5), so geometry must move the opposite way to
    /// reproduce the filter's appearance. Issue #555 §2.3 writes "+", but the
    /// playground bake — the look's source of truth — uses "−".
    /// Noise is always sampled at the un-displaced point.
    func displace(_ point: CGPoint, using noise: SVGTurbulence) -> CGPoint {
        let x = Double(point.x)
        let y = Double(point.y)
        let r = Self.unitClamp(
            (noise.turbulence(channel: 0, x: x, y: y, baseFrequency: frequency, octaves: octaves) + 1) / 2
        ) - 0.5
        let g = Self.unitClamp(
            (noise.turbulence(channel: 1, x: x, y: y, baseFrequency: frequency, octaves: octaves) + 1) / 2
        ) - 0.5
        return CGPoint(x: x - amplitude * r, y: y - amplitude * g)
    }

    private static func unitClamp(_ value: Double) -> Double {
        value < 0 ? 0 : (value > 1 ? 1 : value)
    }
}
