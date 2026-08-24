import SwiftUI

/// The lockup under the fan, as a two-axis roll: swipe left and right to change
/// polarity, up and down to change size.
///
/// The roll is 1:1 with the finger — the content travels exactly as far as the
/// hand does — and detents at the half step, so the answer changes under the
/// thumb rather than on release. Each detent springs the new value to centre
/// and ticks the Taptic Engine, which is what makes it read as magnetic rather
/// than as a slider. The ends clamp instead of wrapping, with rubber-band
/// resistance past the last step, so a hard swipe cannot loop the user back
/// where they started.
///
/// Nothing moves that is not changing. The block is anchored to its bottom
/// edge and the size pyramid is always three marks tall, so rolling between
/// sizes never shifts the layout: the word swaps size in place and the pyramid
/// lights a different mark. On the polarity axis only the word row travels —
/// the span reads the same for all three polarities, so sliding it would be
/// motion that says nothing.
struct ValenceRollView: View {
    let valence: Valence
    let onChange: (Valence) -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// Finger travel per step. Also the distance the neighbour words sit out
    /// at, because the two have to agree for the roll to feel 1:1.
    private static let polarityStep: CGFloat = 220
    private static let sizeStep: CGFloat = 90
    /// How far past the last step the content will stretch.
    private static let overscroll: CGFloat = 34
    private static let neighbourOpacity: Double = 0.22

    private enum Axis { case polarity, size }

    /// Captured when a drag starts: every frame resolves against where the roll
    /// was when the finger landed, never against the value it has drifted to.
    @State private var origin: Valence?
    @State private var axis: Axis?
    /// Horizontal only. The size axis deliberately does not translate: a block
    /// that slid vertically would drag the whole lockup past its neighbours,
    /// and the detent plus the pyramid already say what changed.
    @State private var offset: CGFloat = 0

    var body: some View {
        VStack(spacing: Spacing.xs) {
            wordRow

            Text(valence.headline.span)
                .pebblesFont(.cardHeading)
                .foregroundStyle(Color.system.secondary)

            pyramid
                .padding(.top, Spacing.sm)
        }
        .frame(maxWidth: .infinity)
        .contentShape(Rectangle())
        // High priority rather than plain: the step's ScrollView would
        // otherwise claim every vertical drag and the size axis would be dead
        // on any screen tall enough to scroll.
        .highPriorityGesture(drag)
        // The neighbour words are meant to bleed off the screen edges.
        .accessibilityElement(children: .combine)
    }

    // MARK: - Content

    /// The only part that travels on the polarity axis.
    private var wordRow: some View {
        ZStack {
            if let before = valence.polarityBefore {
                neighbour(before, at: -Self.polarityStep)
            }
            if let after = valence.polarityAfter {
                neighbour(after, at: Self.polarityStep)
            }
            ValenceHeadlineView(valence: valence, showsSpan: false)
        }
        .offset(x: offset)
    }

    /// A neighbouring polarity, one step out. Rendered at the current size so
    /// the row reads as one line of type, and faded so it never competes with
    /// the answer.
    private func neighbour(_ polarity: ValencePolarity, at distance: CGFloat) -> some View {
        ValenceHeadlineView(
            valence: .at(polarity: polarity, size: valence.sizeGroup),
            showsSpan: false
        )
        .opacity(Self.neighbourOpacity)
        .offset(x: distance)
        .allowsHitTesting(false)
    }

    /// Three marks, widest at the top, with the current size lit. Always all
    /// three: a pyramid that changed height would move everything above it,
    /// which is the shift this layout exists to avoid.
    private var pyramid: some View {
        VStack(spacing: 6) {
            ForEach(ValenceSizeGroup.ladder, id: \.self) { size in
                Capsule()
                    .fill(Color.accent.primary.opacity(size == valence.sizeGroup ? 1 : 0.25))
                    .frame(width: markWidth(size), height: 6)
            }
        }
        .animation(.snappy(duration: 0.2), value: valence.sizeGroup)
    }

    private func markWidth(_ size: ValenceSizeGroup) -> CGFloat {
        switch size {
        case .small:  return 8
        case .medium: return 26
        case .large:  return 44
        }
    }

    // MARK: - Gesture

    private var drag: some Gesture {
        DragGesture(minimumDistance: 6)
            .onChanged { value in
                let origin = origin ?? valence
                if self.origin == nil { self.origin = origin }

                // The axis is decided once per drag and held. Without the lock
                // a diagonal swipe alternates axes frame to frame and the roll
                // shakes instead of rolling.
                let axis = axis ?? (
                    abs(value.translation.width) > abs(value.translation.height) ? Axis.polarity : .size
                )
                if self.axis == nil { self.axis = axis }

                let travel = axis == .polarity ? value.translation.width : value.translation.height
                let step = axis == .polarity ? Self.polarityStep : Self.sizeStep

                // Content follows the finger, so dragging left brings the value
                // on the right to centre: the index moves against the travel.
                let wanted = Int((-travel / step).rounded())
                let next = destination(from: origin, axis: axis, steps: wanted)

                if next != valence {
                    if !reduceMotion { TapHaptics.play(.selection) }
                    withAnimation(.spring(response: 0.26, dampingFraction: 0.74)) {
                        onChange(next)
                    }
                }

                // Whatever travel the steps did not consume is what the content
                // is still holding, so it eases back to centre as each detent
                // passes and stretches when there is nothing left to move to.
                let taken = axis == .polarity
                    ? next.polarityIndex - origin.polarityIndex
                    : next.sizeIndex - origin.sizeIndex
                let remainder = travel + CGFloat(taken) * step
                offset = axis == .polarity ? rubberBanded(remainder, limit: step / 2) : 0
            }
            .onEnded { _ in
                withAnimation(.spring(response: 0.34, dampingFraction: 0.7)) {
                    offset = 0
                }
                origin = nil
                axis = nil
            }
    }

    /// Where `steps` along `axis` lands, clamped to the grid.
    private func destination(from origin: Valence, axis: Axis, steps: Int) -> Valence {
        switch axis {
        case .polarity:
            return .at(polarityIndex: origin.polarityIndex + steps, sizeIndex: origin.sizeIndex)
        case .size:
            return .at(polarityIndex: origin.polarityIndex, sizeIndex: origin.sizeIndex + steps)
        }
    }

    /// Travel past `limit` keeps moving, but at a quarter rate and capped, so
    /// the end of the grid feels like a wall with give rather than a stop.
    private func rubberBanded(_ value: CGFloat, limit: CGFloat) -> CGFloat {
        guard abs(value) > limit else { return value }
        let excess = abs(value) - limit
        let damped = min(Self.overscroll, excess * 0.25)
        return value < 0 ? -(limit + damped) : limit + damped
    }
}

#Preview("roll") {
    @Previewable @State var valence: Valence = .neutralMedium
    return ValenceRollView(valence: valence) { valence = $0 }
        .padding(.vertical, 40)
}
