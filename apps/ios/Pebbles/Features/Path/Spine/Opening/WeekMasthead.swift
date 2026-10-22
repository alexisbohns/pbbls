import SwiftUI

/// The page's masthead (#1130 §View tree, D9): the way back a week on the
/// leading side ("week 35", a 44 pt target) when the pager has a previous page,
/// naming that page's own number; the ISO week number in ink on the trailing side.
struct WeekMasthead: View {
    let page: SpinePage
    let onFocus: (Date) -> Void

    var body: some View {
        HStack(alignment: .center) {
            if let previous = page.previous {
                Button { onFocus(previous.weekStart) } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "chevron.left").imageScale(.small)
                        Text(verbatim: SpineCopy.weekLink(previous.number, locale: SpineStrings.appLocale))
                            .spineType(.masthead)
                    }
                    .frame(minWidth: SpineLayout.minTarget, minHeight: SpineLayout.minTarget, alignment: .leading)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
            }
            Spacer(minLength: 0)
            Text(verbatim: "\(page.words.number)")
                .spineType(.mastheadNumber)
                .foregroundStyle(Color.pebbles.onSurface)
                .frame(minHeight: SpineLayout.minTarget)
                .accessibilityHidden(true)
        }
        .padding(.horizontal, SpineLayout.versoPadX)
    }
}
