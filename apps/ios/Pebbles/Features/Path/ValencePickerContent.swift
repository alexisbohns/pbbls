import SwiftUI

/// The valence fan: nine real pebble stones arranged bottom-up, small and
/// near at the bottom, large and spread at the top.
///
/// Presentation only (D5). Shared by `ValencePickerSheet`, which wraps it with
/// a Cancel toolbar and dismisses on pick, and the record flow's valence step,
/// which commits on tap and advances. Same fan, different commit semantics.
///
/// The day/week/month wording that used to head each row is gone from the
/// screen — size carries it now — but `ValenceSizeGroup.name` still forms the
/// VoiceOver label, so a screen reader hears "Day event, Highlight" as before.
struct ValencePickerContent: View {
    let selected: Valence?
    let onSelect: (Valence) -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// Opacity of the eight stones that are not the chosen one. With nothing
    /// chosen, all nine stay at full strength.
    private static let dimmedOpacity: Double = 0.35
    private static let selectedScale: CGFloat = 1.08
    /// Apple's minimum comfortable target; the small stones are under it on
    /// both axes.
    private static let minimumHitTarget: CGFloat = 44

    var body: some View {
        VStack(spacing: Spacing.md) {
            fan
            caption
        }
    }

    private var fan: some View {
        GeometryReader { proxy in
            // The layout is authored in a fixed reference canvas and scaled to
            // whatever width we are given, so the fan reads identically on
            // every device.
            let scale = proxy.size.width / ValenceFanLayout.reference.width
            ZStack {
                ForEach(Valence.allCases) { valence in
                    stone(valence, scale: scale)
                }
            }
        }
        .aspectRatio(
            ValenceFanLayout.reference.width / ValenceFanLayout.reference.height,
            contentMode: .fit
        )
        .frame(maxWidth: ValenceFanLayout.reference.width * 1.2)
        .animation(.snappy(duration: 0.22), value: selected)
    }

    @ViewBuilder
    private func stone(_ valence: Valence, scale: CGFloat) -> some View {
        let isActive = (valence == selected)
        let hasSelection = (selected != nil)
        let centre = ValenceFanLayout.centre(for: valence)
        let height = ValenceFanLayout.stoneHeight(for: valence.sizeGroup) * scale
        let width = ValenceFanLayout.stoneWidth(for: valence.sizeGroup) * scale

        Button {
            onSelect(valence)
        } label: {
            ValenceStoneView(valence: valence, height: height)
                .frame(
                    width: max(width, Self.minimumHitTarget),
                    height: max(height, Self.minimumHitTarget)
                )
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .scaleEffect(isActive && !reduceMotion ? Self.selectedScale : 1)
        .opacity(hasSelection && !isActive ? Self.dimmedOpacity : 1)
        .position(x: centre.x * scale, y: centre.y * scale)
        .accessibilityLabel(Text(
            "\(String(localized: valence.sizeGroup.name)), \(String(localized: valence.shortLabel))"
        ))
        .accessibilityAddTraits(isActive ? [.isSelected] : [])
    }

    private var caption: some View {
        Text(selected?.caption ?? LocalizedStringResource("Pick the one that fits."))
            .pebblesFont(.subhead)
            .foregroundStyle(Color.system.secondary)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
            .animation(nil, value: selected)
    }
}

#Preview("nothing selected") {
    ValencePickerContent(selected: nil, onSelect: { _ in }).padding()
}

#Preview("highlightMedium selected") {
    ValencePickerContent(selected: .highlightMedium, onSelect: { _ in }).padding()
}

#Preview("lowlightLarge selected") {
    ValencePickerContent(selected: .lowlightLarge, onSelect: { _ in }).padding()
}
