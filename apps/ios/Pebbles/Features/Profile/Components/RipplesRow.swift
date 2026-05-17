import SwiftUI

struct RipplesRow: View {
    let ripple: RippleSummary?
    let assiduity: [Bool]?

    var body: some View {
        HStack(alignment: .center, spacing: Spacing.lg) {
            RippleBadge(
                level: ripple?.rippleLevel ?? 0,
                activeToday: ripple?.activeToday ?? false
            )

            VStack(alignment: .leading) {
                Text("Ripples Level \(ripple?.rippleLevel ?? 0)")
                    .pebblesFont(.headline)
                    .foregroundStyle(Color.system.foreground)
                Text(progressCopy)
                    .pebblesFont(.subhead)
                    .foregroundStyle(Color.system.secondary)
            }

            Spacer(minLength: 8)

            AssiduityGrid(data: assiduity ?? Array(repeating: false, count: 28))
        }
    }

    private var progressCopy: LocalizedStringResource {
        guard let ripple else { return "Loading…" }
        if let remaining = ripple.pebblesToNextLevel, let next = ripple.nextLevel {
            return "\(remaining) more pebbles to level \(next)"
        } else {
            return "Max level reached"
        }
    }
}

#Preview("Engaged") {
    RipplesRow(
        ripple: RippleSummary(rippleLevel: 3, pebbles28d: 11, activeToday: true),
        assiduity: (0..<28).map { $0 % 2 == 0 }
    )
    .padding()
}

#Preview("Empty") {
    RipplesRow(ripple: nil, assiduity: nil)
        .padding()
}
