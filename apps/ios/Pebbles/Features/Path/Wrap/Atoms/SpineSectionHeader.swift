import SwiftUI

/// A section's head (#1226): the small-caps label (a heading), an optional
/// italic `hint` beside it, and an optional `trailing` at the far end, all on
/// the label's first baseline. At accessibility text sizes the hint moves under
/// the label, so neither is squeezed a word to a line.
struct SpineSectionHeader<Trailing: View>: View {
    let label: String
    var hint: String?
    let trailing: Trailing

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    init(_ label: String, hint: String? = nil, @ViewBuilder trailing: () -> Trailing) {
        self.label = label
        self.hint = hint
        self.trailing = trailing()
    }

    var body: some View {
        if dynamicTypeSize.isAccessibilitySize, hint != nil {
            VStack(alignment: .leading, spacing: 2) {
                HStack(alignment: .firstTextBaseline, spacing: 0) {
                    labelText
                    Spacer(minLength: 8)
                    trailing
                }
                hintText
            }
        } else {
            HStack(alignment: .firstTextBaseline, spacing: 0) {
                labelText
                hintText.padding(.leading, 8)
                Spacer(minLength: 8)
                trailing
            }
        }
    }

    // The label takes its width first, so the hint wraps, never the word.
    private var labelText: some View {
        Text(verbatim: label)
            .spineType(.sectionLabel)
            .foregroundStyle(Color.pebbles.onSurfaceVariant)
            .layoutPriority(1)
            .accessibilityAddTraits(.isHeader)
    }

    @ViewBuilder
    private var hintText: some View {
        if let hint {
            Text(verbatim: hint)
                .spineType(.sectionHint)
                .foregroundStyle(Color.pebbles.outline)
        }
    }
}

extension SpineSectionHeader where Trailing == EmptyView {
    init(_ label: String, hint: String? = nil) {
        self.init(label, hint: hint) { EmptyView() }
    }
}
