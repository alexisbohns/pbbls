import SwiftUI

/// A Spine page as a plain `VStack` (#1130 D4): the B2 references (which cannot
/// render a `ScrollView`) and the lab lay the rows out here, in the order
/// `SpineWeekRows` gives. The ground runs behind the days block only.
/// It takes no slot focus: `.accessibilityFocused` blanks a slot under
/// `ImageRenderer`, and VoiceOver's return focus belongs to the lazy page.
struct SpineWeekStack: View {
    enum Part { case whole, verso, days, closing }

    let context: SpineFeedContext
    /// A reference slice of the days; nil draws them all.
    var days: ClosedRange<Int>?
    var part: Part = .whole
    var width: CGFloat = 393

    var body: some View {
        VStack(spacing: 0) {
            if part == .whole || part == .verso {
                rows(SpineWeekRows.verso(context.page))
            }
            if part == .whole || part == .days {
                let dayRows = SpineWeekRows.days(context.page, only: days)
                if !dayRows.isEmpty {
                    rows(dayRows)
                        .background(alignment: .top) { SpineGround() }
                        .padding(.top, SpineLayout.feedTop - SpineLayout.seamOverhang)
                }
            }
            if part == .whole || part == .closing {
                rows(SpineWeekRows.closing(context.page))
            }
        }
        .padding(.bottom, SpineLayout.feedBottom)
        .frame(width: width)
        .background(Color.pebbles.surface)
    }

    private func rows(_ rows: [SpineRow]) -> some View {
        VStack(spacing: 0) {
            ForEach(rows) { row in
                SpineRowView(row: row, context: context, slotFocus: nil)
            }
        }
    }
}
