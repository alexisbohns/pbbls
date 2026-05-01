import RiveRuntime
import SwiftUI

/// Cold-launch splash that plays the bundled `pbbls-logo.riv` artwork
/// centered at 33% of screen width. After `Self.duration` seconds the
/// closure `onComplete` fires so `RootView` can transition to the next
/// screen. Reduce Motion still presents the same Rive frame; the runtime
/// handles its own animation pace, and the splash duration is bounded so
/// the user is never left waiting.
struct SplashView: View {
    let onComplete: () -> Void

    /// Total time the splash is held on-screen before `onComplete` fires.
    /// Tuned to roughly match the Rive timeline; if the timeline changes,
    /// adjust here.
    static let duration: TimeInterval = 1.8

    @State private var didStart = false
    @State private var viewModel = RiveViewModel(fileName: "pbbls-logo")

    var body: some View {
        ZStack {
            Color.pebblesBackground.ignoresSafeArea()

            viewModel.view()
                .containerRelativeFrame(.horizontal) { width, _ in width * 0.33 }
                .aspectRatio(1, contentMode: .fit)
                .accessibilityHidden(true)
        }
        .onAppear(perform: start)
    }

    private func start() {
        guard !didStart else { return }
        didStart = true
        viewModel.play()
        DispatchQueue.main.asyncAfter(deadline: .now() + Self.duration) {
            onComplete()
        }
    }
}

#Preview {
    SplashView(onComplete: {})
}
