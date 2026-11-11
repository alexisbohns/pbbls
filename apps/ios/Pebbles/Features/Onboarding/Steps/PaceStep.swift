import SwiftUI

/// Step 6 (#1510 D11, D12): the four paces as one vertical group, each with its icon and a line under
/// its name, the chosen one a filled capsule that bleeds a little past its neighbours. Continue sends
/// everything (`complete_onboarding`), so it loads while `isCompleting` and `failed` is the failed
/// send's line. A radio set by role: exactly one row is chosen, once any is.
struct PaceStep: View {
    let selected: OnboardingPace?
    let isCompleting: Bool
    let failed: Bool
    let onSelect: (OnboardingPace) -> Void
    let onFinish: () -> Void
    /// Reference renders pass false: `ImageRenderer` draws no `ScrollView` content.
    var scrolls = true

    private static let outer: CGFloat = 32
    private static let inner: CGFloat = 10
    /// How far the chosen capsule reaches past the group on each side, as the mock draws it.
    private static let bleed: CGFloat = 6
    private static let gap: CGFloat = 4
    /// The icon disc grows with the text, so a larger icon never squeezes it.
    @ScaledMetric(relativeTo: .body) private var disc: CGFloat = 52

    var body: some View {
        OnboardingStepScaffold(title: "What pace suits you?", scrolls: scrolls) {
            VStack(spacing: Self.gap) {
                ForEach(Array(OnboardingPace.allCases.enumerated()), id: \.element) { index, pace in
                    row(pace, index: index)
                }
            }
            .animation(.snappy, value: selected)
        } footer: {
            footer
        }
    }

    // MARK: - Rows

    private func shape(_ index: Int) -> UnevenRoundedRectangle {
        let last = OnboardingPace.allCases.count - 1
        let top = index == 0 ? Self.outer : Self.inner
        let bottom = index == last ? Self.outer : Self.inner
        return UnevenRoundedRectangle(
            topLeadingRadius: top, bottomLeadingRadius: bottom,
            bottomTrailingRadius: bottom, topTrailingRadius: top,
            style: .continuous
        )
    }

    private func row(_ pace: OnboardingPace, index: Int) -> some View {
        let on = pace == selected
        let ink = on ? Color.pebbles.onPrimary : Color.pebbles.onSurface
        return Button { onSelect(pace) } label: {
            HStack(spacing: Spacing.lg) {
                PbblsGlyphView(pace.icon, .tile, style: AnyShapeStyle(ink))
                    .frame(width: disc, height: disc)
                    .background(Circle().fill(on ? Color.pebbles.onPrimary.opacity(0.18)
                                                 : Color.pebbles.surfaceContainerHighest))
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text(pace.label)
                        .spineType(.wrapMonth)
                        .foregroundStyle(ink)
                    Text(pace.detail)
                        .pebblesFont(.subhead)
                        .foregroundStyle(on ? Color.pebbles.onPrimary : Color.pebbles.onSurfaceVariant)
                }
                .multilineTextAlignment(.leading)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.vertical, Spacing.md)
            .frame(maxWidth: .infinity, minHeight: 88, alignment: .leading)
            .background {
                if on {
                    Capsule().fill(Color.pebbles.primary).padding(.horizontal, -Self.bleed)
                } else {
                    shape(index).fill(Color.pebbles.surfaceContainer)
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(isCompleting)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(on ? .isSelected : [])
    }

    // MARK: - Footer

    private var footer: some View {
        VStack(spacing: Spacing.sm) {
            if failed {
                Text("Couldn't finish. Check your connection and try again.")
                    .pebblesFont(.subhead)
                    .foregroundStyle(Color.pebbles.error)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            Text("No streaks, no guilt.")
                .pebblesFont(.subhead)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
            Button(action: onFinish) { Text("Continue") }
                .buttonStyle(PebblesPrimaryButtonStyle(isLoading: isCompleting))
                .disabled(isCompleting)
        }
    }
}
