import SwiftUI

/// The week roll (#1130 §View tree): the ISO week numerals in 56 × 44 cells, in
/// ink-2; the focused week's in ink over a 1 pt underline. A tap focuses that
/// week. It keeps the old roll's VoiceOver label ("Week 41, 15 pebbles").
struct SpineWeekRoll: View {
    let model: PathModel

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView(.horizontal) {
                LazyHStack(spacing: 0) {
                    ForEach(model.entries) { entry in
                        cell(entry).id(entry.weekStart)
                    }
                }
                .padding(.horizontal, 16)
            }
            .scrollIndicators(.hidden)
            .onAppear { proxy.scrollTo(model.focusedWeekStart, anchor: .center) }
            .onChange(of: model.focusedWeekStart) { _, week in
                if reduceMotion { proxy.scrollTo(week, anchor: .center) } else {
                    withAnimation { proxy.scrollTo(week, anchor: .center) }
                }
            }
        }
        .frame(height: SpineLayout.minTarget)
    }

    private func cell(_ entry: WeekRollEntry) -> some View {
        let number = model.calendar.component(.weekOfYear, from: entry.weekStart)
        let focused = entry.weekStart == model.focusedWeekStart
        return Button {
            if reduceMotion { model.focus(entry.weekStart) } else { withAnimation { model.focus(entry.weekStart) } }
        } label: {
            Text(verbatim: "\(number)")
                .spineType(.rollNumeral)
                .foregroundStyle(focused ? Color.pebbles.onSurface : Color.pebbles.onSurfaceVariant)
                .frame(width: 56, height: SpineLayout.minTarget)
                .overlay(alignment: .bottom) {
                    if focused {
                        Rectangle().fill(Color.pebbles.onSurface).frame(width: 20, height: 1).padding(.bottom, 8)
                    }
                }
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Week \(number), \(entry.moments.count) pebbles")
        .accessibilityAddTraits(focused ? .isSelected : [])
    }
}
