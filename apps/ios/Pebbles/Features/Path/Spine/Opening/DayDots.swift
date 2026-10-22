import SwiftUI

/// A person's seven days (#1130 R15, A5): a filled ink dot for a day they appear
/// on, a 1 pt inset ring in ink-3 at 0.7 for the others; 7 pt dots, 5 pt gaps,
/// 79 pt wide. One VoiceOver element: "On four days: Monday 5, …".
struct DayDots: View {
    let days: [Bool]
    let label: String

    var body: some View {
        HStack(spacing: SpineLayout.dotGap) {
            ForEach(days.indices, id: \.self) { index in
                Group {
                    if days[index] {
                        Circle().fill(Color.pebbles.onSurface)
                    } else {
                        Circle().inset(by: 0.5).stroke(Color.pebbles.outline.opacity(0.7), lineWidth: 1)
                    }
                }
                .frame(width: SpineLayout.dot, height: SpineLayout.dot)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: label))
    }
}
