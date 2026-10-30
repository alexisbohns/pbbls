import SwiftUI

struct ProfileCountersRow: View {
    let daysPracticed: Int?
    let pebbles: Int?
    let karma: Int?

    var body: some View {
        HStack(alignment: .top, spacing: 0) {
            DataTile(value: daysPracticed, icon: .calendarDate, label: "Days")
            DataTile(value: pebbles,       icon: .pebbleRound,  label: "Pebbles")
            DataTile(value: karma,         icon: .karmaDot,     label: "Karma")
        }
    }
}

#Preview {
    ProfileCountersRow(daysPracticed: 42, pebbles: 137, karma: 1200)
        .padding()
}
