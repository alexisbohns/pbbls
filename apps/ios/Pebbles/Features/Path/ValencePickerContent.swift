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

    /// The phone's lean, read by the chosen stone only. Started with the fan,
    /// stopped with it; a pick re-zeroes so the chosen stone starts level.
    @State private var motion = StoneMotionSource()

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// Opacity of the eight stones that are not the chosen one. With nothing
    /// chosen, all nine stay at full strength.
    private static let dimmedOpacity: Double = 0.35
    private static let selectedScale: CGFloat = 1.14
    /// Apple's minimum comfortable target; the small stones are under it on
    /// both axes.
    private static let minimumHitTarget: CGFloat = 44
    /// The tallest state: the large hand word, the span, and the pyramid.
    private static let captionHeight: CGFloat = 144

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
        .overlay(alignment: .bottom) { columnLabels }
        .overlay(alignment: .leading) { rowLabels }
        .animation(.snappy(duration: 0.22), value: selected)
        .onAppear { motion.start() }
        .onDisappear { motion.stop() }
    }

    @ViewBuilder
    private func stone(_ valence: Valence) -> some View {
        let isActive = (valence == selected)
        let hasSelection = (selected != nil)
        let centre = ValenceFanLayout.centre(for: valence)
        let height = ValenceFanLayout.stoneHeight(for: valence.sizeGroup)
        let width = ValenceFanLayout.stoneWidth(for: valence.sizeGroup)

        Button {
            motion.rezero()
            onSelect(valence)
        } label: {
            ValenceStoneView(valence: valence, height: height, isSelected: isActive, lean: isActive ? motion.lean : nil)
                .frame(
                    width: max(width, Self.minimumHitTarget),
                    height: max(height, Self.minimumHitTarget)
                )
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .scaleEffect(isActive && !reduceMotion ? Self.selectedScale : 1)
        .opacity(hasSelection && !isActive ? Self.dimmedOpacity : 1)
        .zIndex(isActive ? 1 : 0)
        .position(centre)
        .accessibilityLabel(Text(
            "\(String(localized: valence.sizeGroup.name)), \(String(localized: valence.shortLabel))"
        ))
        .accessibilityAddTraits(isActive ? [.isSelected] : [])
    }

    /// Lowlight · Neutral · Highlight under the fan, Month · Week · Day up its
    /// left margin (D7). Meta type in the variant ink; VoiceOver already hears
    /// both axes in each stone's label, so these are decorative.
    private var columnLabels: some View {
        HStack {
            Text(ValencePolarity.lowlight.axisLabel)
            Spacer()
            Text(ValencePolarity.neutral.axisLabel)
            Spacer()
            Text(ValencePolarity.highlight.axisLabel)
        }
        .pebblesFont(.meta)
        .foregroundStyle(Color.pebbles.onSurfaceVariant)
        .padding(.horizontal, Spacing.sm)
        .offset(y: Spacing.lg)
        .accessibilityHidden(true)
    }

    /// Inside the canvas, over the left margin the fan's spread leaves empty
    /// (the nearest stone edge is ~21 pt in), so it fits the 375 pt phones too.
    private var rowLabels: some View {
        VStack(alignment: .leading) {
            Text(ValenceSizeGroup.large.axisLabel)
            Spacer()
            Text(ValenceSizeGroup.medium.axisLabel)
            Spacer()
            Text(ValenceSizeGroup.small.axisLabel)
        }
        .pebblesFont(.meta)
        .foregroundStyle(Color.pebbles.onSurfaceVariant)
        .padding(.vertical, Spacing.xl)
        .offset(x: 4)
        .accessibilityHidden(true)
    }

    /// Reserves the tallest lockup's height so rolling between sizes does not
    /// shove the fan up and down the screen. Before a pick (D7) a ghost stone
    /// and a prompt hold the slot instead of the roll.
    @ViewBuilder
    private var caption: some View {
        Group {
            if let selected {
                ValenceRollView(valence: selected, onChange: onSelect)
            } else {
                VStack(spacing: Spacing.sm) {
                    GhostStone(width: 56)
                    Text("Swipe or tap to choose")
                        .pebblesFont(.cardHeading)
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                }
            }
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
