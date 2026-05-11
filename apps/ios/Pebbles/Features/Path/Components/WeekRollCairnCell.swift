import RiveRuntime
import SwiftUI

/// One cell in the horizontal weeks roll. Renders the cairn Rive
/// animation above the ISO week number. Plays its one-shot whenever
/// `isFocused` flips true; resets to frame 1 when it flips false.
struct WeekRollCairnCell: View {
    let entry: WeekRollEntry
    let isFocused: Bool
    let opacity: Double
    let calendar: Calendar
    let onTap: () -> Void

    @State private var cairn: CairnAnimationViewModel

    init(
        entry: WeekRollEntry,
        isFocused: Bool,
        opacity: Double,
        calendar: Calendar,
        onTap: @escaping () -> Void
    ) {
        self.entry = entry
        self.isFocused = isFocused
        self.opacity = opacity
        self.calendar = calendar
        self.onTap = onTap
        self._cairn = State(initialValue: CairnAnimationViewModel(fileName: "pbbls-cairn"))
    }

    var body: some View {
        let weekNum = calendar.component(.weekOfYear, from: entry.weekStart)
        Button(action: onTap) {
            VStack(spacing: 4) {
                cairn.view()
                    .frame(width: 56, height: 56)
                    .accessibilityHidden(true)
                Text(verbatim: "\(weekNum)")
                    .font(.ysabeauSemibold(13))
                    .foregroundStyle(isFocused ? Color.pebblesAccent : Color.pebblesMutedForeground)
            }
        }
        .buttonStyle(.plain)
        .frame(width: 72)
        .opacity(opacity)
        .onChange(of: isFocused) { _, nowFocused in
            if nowFocused {
                cairn.play()
            } else {
                cairn.reset()
            }
        }
        .onAppear {
            // Initial focused cairn plays its intro on first mount.
            if isFocused { cairn.play() }
        }
        .accessibilityLabel("Week \(weekNum), \(entry.pebbles.count) pebbles")
    }
}
