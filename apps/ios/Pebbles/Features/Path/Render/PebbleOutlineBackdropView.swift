import SwiftUI
import SVGView
import os

/// Renders a per-(size × polarity) pebble silhouette behind the
/// composed pebble artwork. Fill-only — no stroke. Color injection
/// follows the same sentinel-swap pattern as `PebbleRenderView`: the
/// asset ships with `fill="#FF00FF"` and the view replaces it at
/// construction time.
///
/// The view fills its proposed frame via `.aspectRatio(.fit)`;
/// consumers compose it inside a `ZStack` and apply the matching
/// `PebbleOutlineGeometry.aspectRatio(for:)` on the outer container so
/// the pebble + backdrop share a single on-screen rectangle.
struct PebbleOutlineBackdropView: View {
    let size: ValenceSizeGroup
    let polarity: ValencePolarity
    let fillHex: String

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "pebble-outline")

    private var coloredSvg: String? {
        // Asset naming: "{size}-{polarity}.svg" (e.g. "small-neutral.svg") —
        // matches ValenceSizeGroup.rawValue + ValencePolarity.rawValue exactly.
        let name = "\(size.rawValue)-\(polarity.rawValue)"
        guard let url = Bundle.main.url(forResource: name, withExtension: "svg"),
              let raw = try? String(contentsOf: url, encoding: .utf8) else {
            Self.logger.error("missing outline asset: \(name, privacy: .public).svg")
            // Bundled-resource miss is a setup bug (xcodegen didn't pick the
            // file up, or it was deleted). We log + render transparent rather
            // than fatalError because a missing asset would otherwise crash
            // the app on every scroll frame that lays out this view. The
            // log line surfaces the bug; the empty cell makes it visible.
            return nil
        }
        let safeHex = fillHex.count == 9 ? String(fillHex.prefix(7)) : fillHex
        return raw.replacingOccurrences(of: "#FF00FF", with: safeHex)
    }

    var body: some View {
        Group {
            if let svg = coloredSvg {
                SVGView(string: svg)
                    .aspectRatio(contentMode: .fit)
            } else {
                Color.clear
            }
        }
        .accessibilityHidden(true)
    }
}

#Preview {
    HStack(spacing: 16) {
        PebbleOutlineBackdropView(size: .small,  polarity: .neutral,   fillHex: "#C07A7A")
        PebbleOutlineBackdropView(size: .medium, polarity: .lowlight,  fillHex: "#5C7AB8")
        PebbleOutlineBackdropView(size: .large,  polarity: .highlight, fillHex: "#D4A85E")
    }
    .frame(height: 200)
    .padding()
}
