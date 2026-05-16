import SwiftUI

struct ProfileStatsCard: View {
    let ripple: RippleSummary?
    let assiduity: [Bool]?
    let daysPracticed: Int?
    let pebbles: Int?
    let karma: Int?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("STATS")
                .font(.caption.weight(.semibold))
                .tracking(0.8)
                .foregroundStyle(Color.pebblesMutedForeground)

            RipplesRow(ripple: ripple, assiduity: assiduity)

            Divider().overlay(Color.pebblesMutedForeground.opacity(0.3))

            ProfileCountersRow(daysPracticed: daysPracticed, pebbles: pebbles, karma: karma)
        }
        .padding(16)
        .background(Color.pebblesListRow)
        .clipShape(RoundedRectangle(cornerRadius: 16))
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
