import CoreGraphics
import Foundation

/// How a domain's name rings a postmark (#1146 §Postmark): `text` is the name
/// in the locale's capitals, repeated with " · " after each, set at `size` with
/// `tracking` added after every character, so the ring closes exactly and the
/// seam is an ordinary gap. A port of Android's `RingFit.kt`.
struct RingFit: Equatable {
    let text: String
    let repetitions: Int
    let size: CGFloat
    let tracking: CGFloat
}

enum RingFits {
    /// Tried in order; a name that cannot ring twice at the last is set once.
    static let sizes: [CGFloat] = [8.5, 8, 7.5]
    static let separator = " · "
    private static let minRepetitions = 2

    /// - Parameters:
    ///   - circumference: the circle the text's baseline runs on, in points.
    ///   - measure: the advance of a string at a size, in points. On device the
    ///     sum of the per-character advances the draw loop steps by, so the ring
    ///     closes exactly; a fake in tests.
    static func fit(name: String, circumference: CGFloat, locale: Locale,
                    measure: (_ text: String, _ size: CGFloat) -> CGFloat) -> RingFit {
        let unit = name.uppercased(with: locale) + separator
        for size in sizes {
            var repetitions = Int((circumference / measure(unit, size)).rounded(.down))
            // A real measure is not additive (kerning, rounding), so the repeated
            // text can overrun where the unit's multiple did not: step down until it
            // fits, so the tracking can never go negative.
            while repetitions >= minRepetitions,
                  measure(String(repeating: unit, count: repetitions), size) > circumference {
                repetitions -= 1
            }
            if repetitions >= minRepetitions {
                let text = String(repeating: unit, count: repetitions)
                return RingFit(text: text, repetitions: repetitions, size: size,
                               tracking: (circumference - measure(text, size)) / CGFloat(text.count))
            }
        }
        let size = sizes[sizes.count - 1]
        return RingFit(text: unit, repetitions: 1, size: size,
                       tracking: max(0, (circumference - measure(unit, size)) / CGFloat(unit.count)))
    }
}
