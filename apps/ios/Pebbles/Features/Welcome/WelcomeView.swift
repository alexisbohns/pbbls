import SwiftUI
import UIKit

/// Pre-login landing. Persistent logo header, paged carousel of
/// `WelcomeSteps.all`, and two stacked CTAs that route into `AuthView`
/// with the correct mode. The carousel auto-advances every 4 seconds;
/// any change to `currentIndex` (programmatic or manual swipe) resets
/// the timer. Reduce Motion short-circuits the loop — swipe only.
///
/// Navigation is owned by the parent: this view invokes `onCreateAccount`
/// and `onLogin` closures so `RootView` can drive the `NavigationPath`.
struct WelcomeView: View {
    let onCreateAccount: () -> Void
    let onLogin: () -> Void

    @State private var currentIndex: Int = 0
    @State private var autoAdvanceTick: Int = 0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    init(onCreateAccount: @escaping () -> Void, onLogin: @escaping () -> Void) {
        self.onCreateAccount = onCreateAccount
        self.onLogin = onLogin

        // Page dot tint mirrors `OnboardingView`: accent for current,
        // muted foreground for inactive.
        UIPageControl.appearance().currentPageIndicatorTintColor = UIColor(named: "AccentColor")
        UIPageControl.appearance().pageIndicatorTintColor = UIColor(named: "MutedForeground")
    }

    var body: some View {
        VStack(spacing: 0) {
            Spacer(minLength: 48)

            Image("WelcomeLogo")
                .renderingMode(.template)
                .resizable()
                .scaledToFit()
                .foregroundStyle(Color.pebblesForeground)
                .frame(maxWidth: 220, maxHeight: 220)

            Spacer()

            TabView(selection: $currentIndex) {
                ForEach(Array(WelcomeSteps.all.enumerated()), id: \.element.id) { index, step in
                    WelcomeSlideView(step: step)
                        .accessibilityElement(children: .combine)
                        .accessibilityLabel(
                            "Welcome step \(index + 1) of \(WelcomeSteps.all.count): "
                            + "\(String(localized: step.title)). "
                            + "\(String(localized: step.description))"
                        )
                        .tag(index)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .always))
            .frame(height: 160)

            VStack(spacing: 12) {
                Button {
                    onCreateAccount()
                } label: {
                    Text("Create an account")
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                }
                .buttonStyle(.borderedProminent)

                Button {
                    onLogin()
                } label: {
                    Text("Log in")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                }
                .buttonStyle(.bordered)
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 32)
        }
        .task(id: autoAdvanceTick) {
            guard !reduceMotion else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(4))
                if Task.isCancelled { break }
                withAnimation {
                    currentIndex = (currentIndex + 1) % WelcomeSteps.all.count
                }
            }
        }
        .onChange(of: currentIndex) { _, _ in
            autoAdvanceTick &+= 1
        }
        .pebblesScreen()
    }
}

#Preview {
    WelcomeView(
        onCreateAccount: {},
        onLogin: {}
    )
}
