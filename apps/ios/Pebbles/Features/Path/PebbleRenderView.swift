import SwiftUI
import SVGView

/// Renders a server-composed pebble SVG string.
///
/// Slice 1: static display only. The animation manifest consumer is a
/// later slice. Fills width and scales to fit; aspect ratio is preserved.
struct PebbleRenderView: View {
    let svg: String
    var strokeColor: String?

    private var coloredSvg: String {
        guard let color = strokeColor else { return svg }
        return svg.replacingOccurrences(of: "currentColor", with: color)
    }

    var body: some View {
        SVGView(string: coloredSvg)
            .aspectRatio(contentMode: .fit)
            .accessibilityHidden(true)
    }
}

#Preview {
    PebbleRenderView(
        svg: """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
              <circle cx="50" cy="50" r="40" fill="none" stroke="currentColor" stroke-width="2"/>
            </svg>
            """,
        strokeColor: "#EF4444"
    )
    .frame(width: 260, height: 260)
}
