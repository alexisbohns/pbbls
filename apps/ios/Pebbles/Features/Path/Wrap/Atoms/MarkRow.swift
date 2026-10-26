import SwiftUI

/// A list row of the wraps and the Spine (#1226; the web's `30px 1fr auto`): a
/// 30 pt mark column, the title (an optional `subtitle` under it) taking the
/// rest, then an optional `count` and `trailing`; at least 44 pt tall, a rule
/// below. The row is one accessibility element saying `accessibilityText`;
/// with `action` it is a button, `actionHint` its hint. Android: `MarkRow`.
struct MarkRow<Leading: View, Trailing: View>: View {
    let title: String
    let accessibilityText: String
    var titleType: SpineType.Style = .personName
    var titleColor: Color = Color.pebbles.onSurface
    var subtitle: String?
    var count: Int?
    var action: (() -> Void)?
    var actionHint: String?
    /// Its columns' keys in the month's intro (#1241); nil, it draws as ever.
    var reveal: MarkRowReveal?
    /// The subtitle over the title (a milestone's date, #1241 D12).
    var subtitleFirst = false
    /// At least this tall, past the row minimum (a card's share, D13); and its mark column this wide.
    var height: CGFloat?
    var markWidth: CGFloat?
    let leading: Leading
    let trailing: Trailing

    init(_ title: String, accessibilityText: String, titleType: SpineType.Style = .personName,
         titleColor: Color = Color.pebbles.onSurface, subtitle: String? = nil, count: Int? = nil,
         action: (() -> Void)? = nil, actionHint: String? = nil, reveal: MarkRowReveal? = nil,
         subtitleFirst: Bool = false, height: CGFloat? = nil, markWidth: CGFloat? = nil,
         @ViewBuilder leading: () -> Leading, @ViewBuilder trailing: () -> Trailing) {
        self.title = title
        self.accessibilityText = accessibilityText
        self.titleType = titleType
        self.titleColor = titleColor
        self.subtitle = subtitle
        self.count = count
        self.action = action
        self.actionHint = actionHint
        self.reveal = reveal
        self.subtitleFirst = subtitleFirst
        self.height = height
        self.markWidth = markWidth
        self.leading = leading()
        self.trailing = trailing()
    }

    var body: some View {
        if let action {
            Button(action: action) { row }
                .buttonStyle(.plain)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text(verbatim: accessibilityText))
                .accessibilityAddTraits(.isButton)
                .modifier(OptionalHint(hint: actionHint))
        } else {
            row
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text(verbatim: accessibilityText))
        }
    }

    private var row: some View {
        HStack(spacing: SpineLayout.rowGap) {
            leading
                .frame(width: markWidth ?? SpineLayout.markColumn)
                .introReveal(reveal?.leading, .pop)
            VStack(alignment: .leading, spacing: 0) {
                if subtitleFirst { subtitleText }
                Text(verbatim: title)
                    .spineType(titleType)
                    .foregroundStyle(titleColor)
                    .introReveal(reveal?.title)
                if !subtitleFirst { subtitleText }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            // The words land as one, unless the title and the subtitle land apart.
            .introReveal(reveal?.title == nil ? reveal?.text : nil)
            if let count {
                Text(verbatim: "\(count)")
                    .spineType(.contentsTime)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .introReveal(reveal?.text)
            }
            trailing
                .introReveal(reveal?.trailing)
        }
        .frame(minHeight: max(height ?? 0, SpineLayout.rowMin))
        .overlay(alignment: .bottom) {
            Rectangle().fill(Color.pebbles.rule).frame(height: SpineLayout.rule)
                .introReveal(reveal?.text, .fade)
        }
        .contentShape(Rectangle())
    }
}

extension MarkRow {
    @ViewBuilder
    private var subtitleText: some View {
        if let subtitle {
            Text(verbatim: subtitle)
                .spineType(.contentsTime)
                .foregroundStyle(Color.pebbles.outline)
                .introReveal(reveal?.subtitle)
        }
    }
}

extension MarkRow where Trailing == EmptyView {
    init(_ title: String, accessibilityText: String, titleType: SpineType.Style = .personName,
         titleColor: Color = Color.pebbles.onSurface, subtitle: String? = nil, count: Int? = nil,
         action: (() -> Void)? = nil, actionHint: String? = nil, reveal: MarkRowReveal? = nil,
         subtitleFirst: Bool = false, height: CGFloat? = nil, markWidth: CGFloat? = nil,
         @ViewBuilder leading: () -> Leading) {
        self.init(title, accessibilityText: accessibilityText, titleType: titleType, titleColor: titleColor,
                  subtitle: subtitle, count: count, action: action, actionHint: actionHint, reveal: reveal,
                  subtitleFirst: subtitleFirst, height: height, markWidth: markWidth, leading: leading) {
            EmptyView()
        }
    }
}

private struct OptionalHint: ViewModifier {
    let hint: String?

    func body(content: Content) -> some View {
        if let hint {
            content.accessibilityHint(Text(verbatim: hint))
        } else {
            content
        }
    }
}

/// Which intro keys land a row's columns (#1241): the mark pops; the words,
/// the count and the rule land with `text`; a nil `leading` or `trailing`
/// lands itself (a cairn stone by stone, a person's ring). With `title`, the
/// title and the subtitle land apart (a milestone's name and date, D10).
struct MarkRowReveal {
    let leading: IntroKey?
    let text: IntroKey
    var trailing: IntroKey?
    var title: IntroKey?
    var subtitle: IntroKey?

    /// A list row landing whole on `.row(index)`.
    static func row(_ index: Int) -> MarkRowReveal {
        MarkRowReveal(leading: .row(index), text: .row(index), trailing: .row(index))
    }
}
