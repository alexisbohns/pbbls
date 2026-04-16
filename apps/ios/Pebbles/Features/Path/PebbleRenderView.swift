import SwiftUI
import SVGView

/// Renders a server-composed pebble SVG string.
///
/// Slice 1: static display only. The animation manifest consumer is a
/// later slice. Fills width and scales to fit; aspect ratio is preserved.
struct PebbleRenderView: View {
    let svg: String

    var body: some View {
        SVGView(string: svg)
            .aspectRatio(contentMode: .fit)
            .accessibilityHidden(true)
    }
}

#Preview {
    PebbleRenderView(svg: """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
          <circle cx="50" cy="50" r="40" fill="none" stroke="black" stroke-width="2"/>
        </svg>
        """)
    .frame(width: 260, height: 260)
}
