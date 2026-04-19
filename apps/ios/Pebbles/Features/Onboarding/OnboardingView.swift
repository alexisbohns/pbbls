import SwiftUI
import UIKit

/// Paged onboarding flow. Renders one `OnboardingPageView` per step inside
/// a `TabView` with the iOS page-style indicator. Toolbar exposes a close
/// (`xmark`) and a `Skip` button — both invoke `onFinish`. The last page
/// also shows a full-width prominent "Start your path" button.
///
/// Persistence (`@AppStorage`) lives at the call site so the view stays
/// previewable and the same view serves both initial-gate and replay.
struct OnboardingView: View {
    let steps: [OnboardingStep]
    let onFinish: () -> Void

    @State private var currentIndex: Int = 0

    init(steps: [OnboardingStep], onFinish: @escaping () -> Void) {
        self.steps = steps
        self.onFinish = onFinish

        // SwiftUI's TabView page-dot indicator is a UIPageControl under the
        // hood; the only way to color it is via UIKit appearance proxies.
        // Same approach used by MainTabView for the tab bar.
        UIPageControl.appearance().currentPageIndicatorTintColor = UIColor(named: "AccentColor")
        UIPageControl.appearance().pageIndicatorTintColor = UIColor(named: "MutedForeground")
    }

    var body: some View {
        NavigationStack {
            TabView(selection: $currentIndex) {
                ForEach(Array(steps.enumerated()), id: \.element.id) { index, step in
                    OnboardingPageView(step: step)
                        .tag(index)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .always))
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        onFinish()
                    } label: {
                        Image(systemName: "xmark")
                    }
                    .accessibilityLabel("Close onboarding")
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Skip") {
                        onFinish()
                    }
                }
            }
            .safeAreaInset(edge: .bottom) {
                Group {
                    if currentIndex == steps.count - 1 {
                        Button {
                            onFinish()
                        } label: {
                            Text("Start your path")
                                .foregroundStyle(.white)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 8)
                        }
                        .buttonStyle(.borderedProminent)
                        .padding(.horizontal, 24)
                        .padding(.bottom, 24)
                        .transition(.opacity.combined(with: .move(edge: .bottom)))
                    }
                }
                .animation(.easeInOut(duration: 0.2), value: currentIndex)
            }
            .pebblesScreen()
        }
    }
}

#Preview {
    OnboardingView(steps: OnboardingSteps.all) {
        // no-op preview close
    }
}
