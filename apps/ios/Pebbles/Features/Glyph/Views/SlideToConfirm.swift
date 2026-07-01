import SwiftUI

/// Pure geometry for the slide control — extracted so the threshold logic is
/// testable without a gesture.
enum SlideMath {
    /// Drag translation → 0…1 progress along the track's travel distance.
    static func progress(dragX: CGFloat, trackWidth: CGFloat, thumb: CGFloat) -> Double {
        let travel = max(1, trackWidth - thumb)
        let clamped = max(0, min(dragX, travel))
        return Double(clamped / travel)
    }

    /// A slide counts as confirmed once it crosses `threshold` of the travel.
    static func isConfirmed(_ progress: Double, threshold: Double = 0.9) -> Bool {
        progress >= threshold
    }
}

/// "Slide to confirm" control for the glyph swap. Fills as the thumb is dragged;
/// crossing the threshold fires `onConfirm`. Feedback (haptics + sound) is driven
/// through the injected `GlyphSwapFeedback`.
struct SlideToConfirm: View {
    let cost: Int
    let isEnabled: Bool
    let feedback: GlyphSwapFeedback
    let onConfirm: () async -> Void

    @State private var dragX: CGFloat = 0
    @State private var trackWidth: CGFloat = 0
    @State private var isSliding = false

    private let thumb: CGFloat = 56
    private let height: CGFloat = 56

    var body: some View {
        ZStack(alignment: .leading) {
            Capsule().fill(Color.accent.surface)

            Capsule()
                .fill(Color.accent.light)
                .frame(width: dragX + thumb)

            Text("Slide to confirm")
                .font(.subheadline.weight(.medium))
                .foregroundStyle(Color.accent.primary)
                .frame(maxWidth: .infinity)

            thumbView
                .offset(x: dragX)
        }
        .frame(height: height)
        .background(
            GeometryReader { geo in
                Color.clear
                    .onAppear { trackWidth = geo.size.width }
                    .onChange(of: geo.size.width) { _, width in trackWidth = width }
            }
        )
        .opacity(isEnabled ? 1 : 0.5)
        .clipShape(Capsule())
        .gesture(drag)
        .accessibilityElement()
        .accessibilityLabel("Slide to confirm swap")
        .accessibilityValue("\(cost) karma")
        .accessibilityAddTraits(.isButton)
        .accessibilityAction { if isEnabled { Task { await onConfirm() } } }
    }

    private var thumbView: some View {
        ZStack {
            Circle().fill(Color.accent.primary)
            Text("\(cost)")
                .font(.headline)
                .foregroundStyle(.white)
        }
        .frame(width: thumb, height: thumb)
    }

    private var drag: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                guard isEnabled else { return }
                if !isSliding {
                    isSliding = true
                    feedback.begin()
                }
                let travel = max(1, trackWidth - thumb)
                dragX = max(0, min(value.translation.width, travel))
                feedback.update(progress: SlideMath.progress(dragX: dragX, trackWidth: trackWidth, thumb: thumb))
            }
            .onEnded { _ in
                guard isEnabled else { return }
                isSliding = false
                let progress = SlideMath.progress(dragX: dragX, trackWidth: trackWidth, thumb: thumb)
                if SlideMath.isConfirmed(progress) {
                    feedback.success()
                    withAnimation(.snappy) { dragX = max(1, trackWidth - thumb) }
                    Task { await onConfirm() }
                } else {
                    feedback.cancel()
                    withAnimation(.snappy) { dragX = 0 }
                }
            }
    }
}

#Preview {
    SlideToConfirm(cost: 7, isEnabled: true, feedback: GlyphSwapFeedback(), onConfirm: {})
        .padding()
}
