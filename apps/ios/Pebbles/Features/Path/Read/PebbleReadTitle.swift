import SwiftUI

/// Title block for the pebble read view: serif name + uppercase tracked
/// date, both centered. Sized smaller than the original header so the
/// banner above it can carry the visual weight (issue #331).
///
/// `nameColor` / `dateColor` override the default chrome colors — the read page
/// tints them to the emotion palette (#605). `nil` keeps the system chrome.
struct PebbleReadTitle: View {
    let name: String
    let happenedAt: Date
    var nameColor: Color? = nil
    var dateColor: Color? = nil

    var body: some View {
        VStack(spacing: 6) {
            Text(name)
                .font(.custom("Ysabeau-SemiBold", size: 24))
                .multilineTextAlignment(.center)
                .foregroundStyle(nameColor ?? Color.system.foreground)
            Text(formattedDate)
                .font(.caption)
                .tracking(1.2)
                .textCase(.uppercase)
                .foregroundStyle(dateColor ?? Color.system.secondary)
        }
        .frame(maxWidth: .infinity)
    }

    private var formattedDate: String {
        // Locale-aware. Example en output: "MON, MAR 12, 2026 · 2:32 PM".
        // `.textCase(.uppercase)` on the Text handles casing visually, so we
        // only need a clean, locale-correct format here.
        let date = happenedAt.formatted(
            .dateTime
                .weekday(.abbreviated)
                .month(.abbreviated)
                .day()
                .year()
        )
        let time = happenedAt.formatted(.dateTime.hour().minute())
        return "\(date) · \(time)"
    }
}

#Preview {
    VStack(spacing: 24) {
        PebbleReadTitle(name: "Publication de mon livre", happenedAt: .now)
        PebbleReadTitle(
            name: "A much longer pebble title that needs to wrap onto two lines comfortably",
            happenedAt: .now
        )
    }
    .padding()
    .background(Color.system.background)
}
