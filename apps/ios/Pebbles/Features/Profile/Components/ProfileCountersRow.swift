import SwiftUI

struct ProfileCountersRow: View {
    let daysPracticed: Int?
    let pebbles: Int?
    let karma: Int?

    var body: some View {
        HStack(alignment: .top, spacing: 0) {
            counter(value: daysPracticed, icon: "calendar", label: "Days")
            counter(value: pebbles,       icon: "fossil.shell", label: "Pebbles")
            counter(value: karma,         icon: "sparkles", label: "Karma")
        }
    }

    @ViewBuilder
    private func counter(value: Int?, icon: String, label: LocalizedStringResource) -> some View {
        VStack(spacing: 4) {
            Text(value.map { "\($0)" } ?? "—")
                .font(.title2.weight(.semibold))
                .foregroundStyle(Color.pebblesForeground)
                .monospacedDigit()
            Image(systemName: icon)
                .font(.caption)
                .foregroundStyle(Color.pebblesMutedForeground)
            Text(label)
                .font(.caption2)
                .foregroundStyle(Color.pebblesMutedForeground)
        }
        .frame(maxWidth: .infinity)
    }
}

#Preview {
    ProfileCountersRow(daysPracticed: 42, pebbles: 137, karma: 1200)
        .padding()
}
