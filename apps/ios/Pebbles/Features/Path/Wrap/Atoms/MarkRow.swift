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
    let leading: Leading
    let trailing: Trailing

    init(_ title: String, accessibilityText: String, titleType: SpineType.Style = .personName,
         titleColor: Color = Color.pebbles.onSurface, subtitle: String? = nil, count: Int? = nil,
         action: (() -> Void)? = nil, actionHint: String? = nil,
         @ViewBuilder leading: () -> Leading, @ViewBuilder trailing: () -> Trailing) {
        self.title = title
        self.accessibilityText = accessibilityText
        self.titleType = titleType
        self.titleColor = titleColor
        self.subtitle = subtitle
        self.count = count
        self.action = action
        self.actionHint = actionHint
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
                .frame(width: SpineLayout.markColumn)
            VStack(alignment: .leading, spacing: 0) {
                Text(verbatim: title)
                    .spineType(titleType)
                    .foregroundStyle(titleColor)
                if let subtitle {
                    Text(verbatim: subtitle)
                        .spineType(.contentsTime)
                        .foregroundStyle(Color.pebbles.outline)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if let count {
                Text(verbatim: "\(count)")
                    .spineType(.contentsTime)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
            }
            trailing
        }
        .frame(minHeight: SpineLayout.rowMin)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Color.pebbles.rule).frame(height: SpineLayout.rule)
        }
        .contentShape(Rectangle())
    }
}

extension MarkRow where Trailing == EmptyView {
    init(_ title: String, accessibilityText: String, titleType: SpineType.Style = .personName,
         titleColor: Color = Color.pebbles.onSurface, subtitle: String? = nil, count: Int? = nil,
         action: (() -> Void)? = nil, actionHint: String? = nil, @ViewBuilder leading: () -> Leading) {
        self.init(title, accessibilityText: accessibilityText, titleType: titleType, titleColor: titleColor,
                  subtitle: subtitle, count: count, action: action, actionHint: actionHint, leading: leading) {
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
