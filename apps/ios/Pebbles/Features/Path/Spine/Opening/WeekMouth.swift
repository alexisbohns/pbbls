import SwiftUI

/// Where the week ends (#1130 §View tree, D9): the line's last 26 pt into a
/// three-stone outline cairn (14 / 19 / 25 wide), "the forty-first week ends
/// here", "The forty-second begins on Monday the twelfth." (the calendar's next
/// week: it is about time), and the link to the pager's next page, named by
/// that page's own number, when there is one.
struct WeekMouth: View {
    let page: SpinePage
    let onFocus: (Date) -> Void

    private var locale: Locale { SpineStrings.appLocale }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Rectangle()
                .fill(Color.pebbles.line)
                .frame(width: SpineLayout.line, height: SpineLayout.mouthLine)
                .padding(.leading, SpineLayout.spineX - SpineLayout.line / 2)
                .accessibilityHidden(true)
            cairn
            VStack(alignment: .leading, spacing: 4) {
                Text(verbatim: SpineCopy.weekEnds(ordinal: page.words.ordinal, locale: locale))
                    .spineType(.mouthLabel)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                Text(verbatim: SpineCopy.nextBegins(ordinal: page.words.nextOrdinal, day: page.words.nextMonday,
                                                    locale: locale))
                    .spineType(.mouthLine)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                if let next = page.next {
                    Button { onFocus(next.weekStart) } label: {
                        HStack(spacing: 4) {
                            Text(verbatim: SpineCopy.weekLink(next.number, locale: locale)).spineType(.masthead)
                            Image(systemName: "chevron.right").imageScale(.small)
                        }
                        .frame(minHeight: SpineLayout.minTarget)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .padding(.top, 6)
                }
            }
            .padding(.leading, SpineLayout.colX)
            .padding(.trailing, SpineLayout.padRight)
            .padding(.top, 12)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// Three stone outlines stacked on the line, smallest on top.
    private var cairn: some View {
        VStack(spacing: 2) {
            ForEach([14.0, 19.0, 25.0], id: \.self) { width in
                Ellipse()
                    .stroke(Color.pebbles.line, lineWidth: 1)
                    .frame(width: width, height: width * 0.62)
            }
        }
        .frame(width: 25)
        .padding(.leading, SpineLayout.spineX - 12.5)
        .accessibilityHidden(true)
    }
}
