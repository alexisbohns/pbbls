import SwiftUI

/// Crossfade carousel + custom pagination dots beneath. A simple fade
/// is used (rather than a horizontal slide) so the wrap-around from
/// the last step back to the first stays clean — sliding would have to
/// scroll backwards across all intermediate slides. A `DragGesture`
/// still allows swiping to advance/retreat manually, but the visual is
/// always a crossfade so swipe and auto-advance behave identically.
struct WelcomeCarousel: View {
    @Binding var currentIndex: Int

    private let steps = WelcomeSteps.all

    var body: some View {
        VStack(spacing: 20) {
            ZStack {
                ForEach(Array(steps.enumerated()), id: \.element.id) { index, step in
                    WelcomeSlideView(step: step)
                        .opacity(currentIndex == index ? 1 : 0)
                        .accessibilityElement(children: .combine)
                        .accessibilityLabel(
                            "Welcome step \(index + 1) of \(steps.count): "
                            + "\(String(localized: step.title)). "
                            + "\(String(localized: step.description))"
                        )
                        .accessibilityHidden(currentIndex != index)
                }
            }
            .frame(height: 110)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 20)
                    .onEnded { value in
                        let threshold: CGFloat = 50
                        if value.translation.width < -threshold {
                            withAnimation(.easeInOut(duration: 0.4)) {
                                currentIndex = (currentIndex + 1) % steps.count
                            }
                        } else if value.translation.width > threshold {
                            withAnimation(.easeInOut(duration: 0.4)) {
                                currentIndex = (currentIndex - 1 + steps.count) % steps.count
                            }
                        }
                    }
            )

            HStack(spacing: 8) {
                ForEach(steps.indices, id: \.self) { idx in
                    Circle()
                        .fill(idx == currentIndex ? Color.pebblesAccent : Color.pebblesMuted)
                        .frame(width: 6, height: 6)
                        .animation(.easeInOut(duration: 0.2), value: currentIndex)
                }
            }
            .accessibilityHidden(true)
        }
    }
}

#Preview {
    @Previewable @State var index = 0
    return WelcomeCarousel(currentIndex: $index)
}
