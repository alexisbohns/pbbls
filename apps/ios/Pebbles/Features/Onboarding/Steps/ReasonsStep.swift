import SwiftUI

/// Step 3 (#1510 D4): six equal tiles in two columns, multi-select. A selected tile is
/// `primaryContainer` with a lit stone in its corner.
struct ReasonsStep: View {
    let selected: [OnboardingReason]
    let onToggle: (OnboardingReason) -> Void
    let onContinue: () -> Void
    var scrolls = true

    @Environment(\.dynamicTypeSize) private var typeSize

    private static let rows: [[OnboardingReason]] = stride(from: 0, to: OnboardingReason.allCases.count, by: 2).map {
        Array(OnboardingReason.allCases[$0..<$0 + 2])
    }

    var body: some View {
        OnboardingStepScaffold(title: "What brings you here?", lead: "Pick all that fit.", scrolls: scrolls) {
            VStack(spacing: Spacing.sm) {
                // At accessibility sizes two columns leave too little width: long French
                // labels would break inside a word.
                if typeSize.isAccessibilitySize {
                    ForEach(OnboardingReason.allCases, id: \.self) { reason in
                        tile(reason).fixedSize(horizontal: false, vertical: true)
                    }
                } else {
                    ForEach(Self.rows, id: \.first) { pair in
                        HStack(spacing: Spacing.sm) {
                            ForEach(pair, id: \.self) { tile($0) }
                        }
                        .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
            .padding(.top, Spacing.xs)
        } footer: {
            Button(action: onContinue) { Text("Continue") }
                .buttonStyle(PebblesPrimaryButtonStyle())
                .disabled(selected.isEmpty)
        }
    }

    private func tile(_ reason: OnboardingReason) -> some View {
        let on = selected.contains(reason)
        let ink = on ? Color.pebbles.onPrimaryContainer : Color.pebbles.onSurface
        let shape = RoundedRectangle(cornerRadius: 24, style: .continuous)
        return Button { onToggle(reason) } label: {
            VStack(alignment: .leading, spacing: Spacing.sm) {
                HStack(alignment: .top) {
                    PbblsIconImage(reason.icon, .tile, style: AnyShapeStyle(ink))
                    Spacer(minLength: 0)
                    PbblsIconImage(on ? .pebbleRoundLit1 : .pebbleRound, .row,
                                   style: AnyShapeStyle(on ? Color.pebbles.primary : Color.pebbles.outline))
                }
                Text(reason.label)
                    .pebblesFont(.subhead)
                    .foregroundStyle(ink)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(Spacing.lg)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .frame(minHeight: 112)
            .background(shape.fill(on ? Color.pebbles.primaryContainer : Color.pebbles.surfaceContainerLowest))
            .overlay { if !on { shape.strokeBorder(Color.pebbles.outlineVariant, lineWidth: 1) } }
            .contentShape(shape)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(reason.label))
        .accessibilityAddTraits(on ? .isSelected : [])
    }
}
