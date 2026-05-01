import RiveRuntime
import SwiftUI

/// Pre-login landing AND splash. `RootView` keeps this view mounted for
/// the entire splash hold so the bundled `pbbls-logo.riv` plays through
/// without an interrupting view-swap. While `contentRevealed` is false
/// only the Rive logo is visible, centered at 33% width. When the parent
/// flips `contentRevealed` true, the carousel + sign-in buttons +
/// disclaimer are inserted with a `move(.bottom)` transition that pushes
/// the logo up to its final header position; individual elements then
/// fade in one-by-one via a `revealStep` state machine.
///
/// Email entry buttons (`onCreateAccount` / `onLogin`) push `AuthView` via
/// the parent's `NavigationPath`. OAuth buttons call `SupabaseService`
/// directly — a successful sign-in flips `supabase.session` and `RootView`
/// swaps to the tab bar.
struct WelcomeView: View {
    let contentRevealed: Bool
    let onCreateAccount: () -> Void
    let onLogin: () -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var currentIndex: Int = 0
    @State private var autoAdvanceTick: Int = 0
    @State private var isSubmitting: Bool = false
    @State private var presentedLegalDoc: LegalDoc?
    @State private var revealStep: Int = 0
    @State private var logoViewModel = RiveViewModel(fileName: "pbbls-logo-appear_idle")

    /// Reveal cadence (seconds from the moment `contentRevealed` flips
    /// true). One row per UI element in the order they fade in.
    private static let revealSchedule: [TimeInterval] = [
        0.00,   // 1: content container slides in (carousel + buttons + terms layout)
        0.20,   // 2: carousel
        0.45,   // 3: Create an account
        0.60,   // 4: Log in
        0.75,   // 5: Continue with Apple
        0.90,   // 6: Continue with Google
        1.10    // 7: terms disclaimer
    ]
    private static let fadeDuration: TimeInterval = 0.3
    /// Animation used for the logo's center→top translation as the
    /// content container is inserted.
    private static let layoutAnimation: Animation = .easeInOut(duration: 0.55)

    var body: some View {
        ZStack {
            Color.pebblesBackground.ignoresSafeArea()

            VStack(spacing: 0) {
                Spacer(minLength: 0)

                logoViewModel.view()
                    .containerRelativeFrame(.horizontal) { width, _ in width * 0.33 }
                    .aspectRatio(1, contentMode: .fit)
                    .accessibilityHidden(true)

                Spacer(minLength: 0)

                if revealStep >= 1 {
                    revealedContent
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
            .animation(Self.layoutAnimation, value: revealStep >= 1)
        }
        .task(id: autoAdvanceTick) {
            guard !reduceMotion, revealStep >= 2 else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(4))
                if Task.isCancelled { break }
                withAnimation(.easeInOut(duration: 0.55)) {
                    currentIndex = (currentIndex + 1) % WelcomeSteps.all.count
                }
            }
        }
        .onAppear {
            logoViewModel.play()
            if contentRevealed && revealStep == 0 {
                Task { await runReveal() }
            }
        }
        .onChange(of: contentRevealed) { _, newValue in
            if newValue && revealStep == 0 {
                Task { await runReveal() }
            }
        }
        .onChange(of: currentIndex) { _, _ in
            autoAdvanceTick &+= 1
        }
        .onChange(of: revealStep) { _, newValue in
            // Re-arm the auto-advance loop once the carousel becomes visible.
            if newValue == 2 {
                autoAdvanceTick &+= 1
            }
        }
        .sheet(item: $presentedLegalDoc) { doc in
            LegalDocumentSheet(url: doc.url)
                .ignoresSafeArea()
        }
        .tint(Color.pebblesAccent)
    }

    // MARK: - Revealed content (slides in from below the logo)

    private var revealedContent: some View {
        VStack(spacing: 0) {
            WelcomeCarousel(currentIndex: $currentIndex)
                .opacity(revealStep >= 2 ? 1 : 0)
                .padding(.bottom, 24)

            VStack(spacing: 12) {
                Button {
                    onCreateAccount()
                } label: {
                    Text("Create an account")
                        .fontWeight(.medium)
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                }
                .buttonStyle(.borderedProminent)
                .buttonBorderShape(.capsule)
                .disabled(isSubmitting)
                .opacity(revealStep >= 3 ? 1 : 0)

                Button {
                    onLogin()
                } label: {
                    Text("Log in")
                        .fontWeight(.medium)
                        .foregroundStyle(Color.pebblesAccent)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .overlay(
                            Capsule()
                                .stroke(Color.pebblesAccent, lineWidth: 1)
                        )
                }
                .buttonStyle(.plain)
                .disabled(isSubmitting)
                .opacity(revealStep >= 4 ? 1 : 0)

                Button {
                    Task { await runApple() }
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "applelogo")
                            .font(.body)
                        Text("Continue with Apple")
                            .fontWeight(.medium)
                    }
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                }
                .buttonStyle(.borderedProminent)
                .buttonBorderShape(.capsule)
                .tint(.black)
                .disabled(isSubmitting)
                .opacity(revealStep >= 5 ? 1 : 0)

                Button {
                    Task { await runGoogle() }
                } label: {
                    HStack(spacing: 8) {
                        Image("GoogleGMark")
                            .resizable()
                            .scaledToFit()
                            .frame(width: 18, height: 18)
                        Text("Continue with Google")
                            .fontWeight(.medium)
                    }
                    .foregroundStyle(Color.pebblesForeground)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(Capsule().fill(Color.white))
                    .overlay(Capsule().stroke(Color.pebblesBorder, lineWidth: 1))
                }
                .buttonStyle(.plain)
                .disabled(isSubmitting)
                .opacity(revealStep >= 6 ? 1 : 0)

                if let error = supabase.authError {
                    Text(error)
                        .font(.footnote)
                        .foregroundStyle(.red)
                        .multilineTextAlignment(.center)
                        .padding(.top, 4)
                }

                Text("Read our [Terms](pebbles://legal/terms) and [Privacy](pebbles://legal/privacy) before creating an account with Apple or Google.")
                    .font(.caption)
                    .foregroundStyle(Color.pebblesMutedForeground)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .tint(Color.pebblesAccent)
                    .padding(.top, 8)
                    .opacity(revealStep >= 7 ? 1 : 0)
                    .environment(\.openURL, OpenURLAction { url in
                        switch url.absoluteString {
                        case "pebbles://legal/terms":
                            presentedLegalDoc = .terms
                        case "pebbles://legal/privacy":
                            presentedLegalDoc = .privacy
                        default:
                            break
                        }
                        return .handled
                    })
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 32)
        }
    }

    private func runReveal() async {
        guard revealStep == 0 else { return }
        if reduceMotion {
            withAnimation(nil) { revealStep = Self.revealSchedule.count }
            return
        }
        var previous: TimeInterval = 0
        for (index, delay) in Self.revealSchedule.enumerated() {
            let wait = delay - previous
            if wait > 0 {
                try? await Task.sleep(for: .seconds(wait))
            }
            if Task.isCancelled { return }
            // First step (the layout slide-in) uses the layout animation
            // so logo translation and content insertion stay synchronized.
            // Subsequent steps use the smaller fade for individual elements.
            let animation: Animation = (index == 0) ? Self.layoutAnimation : .easeOut(duration: Self.fadeDuration)
            withAnimation(animation) {
                revealStep = index + 1
            }
            previous = delay
        }
    }

    private func runApple() async {
        guard !isSubmitting else { return }
        isSubmitting = true
        await supabase.signInWithApple()
        isSubmitting = false
    }

    private func runGoogle() async {
        guard !isSubmitting else { return }
        isSubmitting = true
        await supabase.signInWithGoogle()
        isSubmitting = false
    }
}

#Preview("Splash phase") {
    WelcomeView(
        contentRevealed: false,
        onCreateAccount: {},
        onLogin: {}
    )
    .environment(SupabaseService())
}

#Preview("Revealed") {
    WelcomeView(
        contentRevealed: true,
        onCreateAccount: {},
        onLogin: {}
    )
    .environment(SupabaseService())
}
