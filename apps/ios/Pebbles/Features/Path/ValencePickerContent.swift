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
    private static let dimmedOpacity: Double = 0.45
    private static let selectedScale: CGFloat = 1.14
    /// Apple's minimum comfortable target; the small stones are under it on
    /// both axes.
    private static let minimumHitTarget: CGFloat = 44
    /// The tallest state: overtitle, the large hand word, the span, and the
    /// pyramid.
    private static let captionHeight: CGFloat = 168

    var body: some View {
        VStack(spacing: Spacing.md) {
            fan
            caption
        }
    }

    private var fan: some View {
        ZStack {
            ForEach(Valence.allCases) { valence in
                stone(valence)
            }
        }
        .frame(width: ValenceFanLayout.reference.width, height: ValenceFanLayout.reference.height)
        .animation(.snappy(duration: 0.22), value: selected)
    }

    @ViewBuilder
    private func stone(_ valence: Valence) -> some View {
        let isActive = (valence == selected)
        let hasSelection = (selected != nil)
        let centre = ValenceFanLayout.centre(for: valence)
        let height = ValenceFanLayout.stoneHeight(for: valence.sizeGroup)
        let width = ValenceFanLayout.stoneWidth(for: valence.sizeGroup)

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
        // Lifts the chosen stone off the page. Tied to selection rather than to
        // motion, so it still reads under Reduce Motion, where the scale-up
        // is the part that gets dropped.
        .shadow(
            color: Color.system.foreground.opacity(isActive ? 0.22 : 0),
            radius: isActive ? 10 : 0,
            y: isActive ? 4 : 0
        )
        .zIndex(isActive ? 1 : 0)
        .position(centre)
        .accessibilityLabel(Text(
            "\(String(localized: valence.sizeGroup.name)), \(String(localized: valence.shortLabel))"
        ))
        .accessibilityAddTraits(isActive ? [.isSelected] : [])
    }

    /// Reserves the tallest lockup's height so rolling between sizes does not
    /// shove the fan up and down the screen.
    @ViewBuilder
    private var caption: some View {
        Group {
            // The step arrives already parked on a valence, so the roll always
            // has something under the finger. `nil` only happens in previews
            // and in the edit sheet before it stages a value.
            ValenceRollView(valence: selected ?? .neutralMedium, onChange: onSelect)
        }
        // Bottom-anchored: the pyramid and the span sit at a fixed height, and
        // the word and its overtitle grow upward into space already reserved.
        .frame(maxWidth: .infinity, minHeight: Self.captionHeight, alignment: .bottom)
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
