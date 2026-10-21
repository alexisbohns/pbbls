import SwiftUI

/// A Spine page as a plain `VStack` (#1130 D4): the B2 references (which cannot
/// render a `ScrollView`) and the lab lay the rows out here. The ground runs
/// behind the days block only. D4 adds the verso above and the mouth below.
/// It takes no slot focus: `.accessibilityFocused` blanks a slot under
/// `ImageRenderer`, and VoiceOver's return focus belongs to the lazy page.
struct SpineWeekStack: View {
    let context: SpineFeedContext
    /// A reference slice of the days; nil draws them all.
    var days: ClosedRange<Int>?
    var width: CGFloat = 393

    var body: some View {
        VStack(spacing: 0) {
            VStack(spacing: 0) {
                ForEach(SpineWeekRows.days(context.page, only: days)) { row in
                    SpineRowView(row: row, context: context, slotFocus: nil)
                }
            }
            .background(alignment: .top) { SpineGround() }
            .padding(.top, SpineLayout.feedTop - SpineLayout.seamOverhang)
        }
        .padding(.bottom, SpineLayout.feedBottom)
        .frame(width: width)
        .background(Color.pebbles.surface)
    }
}
