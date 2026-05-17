import SwiftUI

struct ProfileStatsCard: View {
    let ripple: RippleSummary?
    let assiduity: [Bool]?
    let daysPracticed: Int?
    let pebbles: Int?
    let karma: Int?

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            Text("Stats")
                .pebblesFont(.cardHeading)
                .foregroundStyle(Color.system.secondary)

            RipplesRow(ripple: ripple, assiduity: assiduity)

            Divider().overlay(Color.system.muted)

            ProfileCountersRow(daysPracticed: daysPracticed, pebbles: pebbles, karma: karma)
        }
        .profileCard()
    }
}

#Preview {
    ProfileStatsCard(
        ripple: RippleSummary(rippleLevel: 3, pebbles28d: 11, activeToday: true),
        assiduity: (0..<28).map { $0 % 2 == 0 },
        daysPracticed: 42,
        pebbles: 137,
        karma: 1200
    )
    .padding()
}
