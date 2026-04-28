import SwiftUI

/// Header section of the pebble read view: the rendered pebble shape, the
/// title in Ysabeau SemiBold, and an uppercased tracked date label below.
///
/// Font: Ysabeau SemiBold is registered via `UIAppFonts` in Info.plist
/// (file `Ysabeau SemiBold.ttf`, PostScript name `Ysabeau-SemiBold`).
struct PebbleReadHeader: View {
    let detail: PebbleDetail

    var body: some View {
        VStack(spacing: 16) {
            if let svg = detail.renderSvg {
                PebbleRenderView(svg: svg, strokeColor: detail.emotion.color)
                    .frame(maxWidth: .infinity)
                    .frame(height: detail.valence.sizeGroup.renderHeight)
            }
            VStack(spacing: 8) {
                Text(detail.name)
                    .font(.custom("Ysabeau-SemiBold", size: 34))
                    .multilineTextAlignment(.center)
                    .foregroundStyle(Color.pebblesForeground)
                Text(formattedDate)
                    .font(.caption)
                    .tracking(1.2)
                    .textCase(.uppercase)
                    .foregroundStyle(Color.pebblesMutedForeground)
            }
        }
        .frame(maxWidth: .infinity)
    }

    private var formattedDate: String {
        // Locale-aware. Example en output: "MON, MAR 12, 2026 · 2:32 PM"
        // .textCase(.uppercase) handles the casing visually so we only need
        // a clean, locale-correct format here.
        let date = detail.happenedAt.formatted(
            .dateTime
                .weekday(.abbreviated)
                .month(.abbreviated)
                .day()
                .year()
        )
        let time = detail.happenedAt.formatted(.dateTime.hour().minute())
        return "\(date) · \(time)"
    }
}
