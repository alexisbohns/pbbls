import SwiftUI
import UIKit

/// Pre-login landing. Persistent logo header, paged carousel of
/// `WelcomeSteps.all`, and four entry buttons:
/// "Create an account", "Log in", "Continue with Apple", "Continue with Google".
/// A passive consent disclosure sits beneath the OAuth buttons.
///
/// Email entry buttons (`onCreateAccount` / `onLogin`) push `AuthView` via
/// the parent's `NavigationPath`. OAuth buttons call `SupabaseService`
/// directly — a successful sign-in flips `supabase.session` and `RootView`
/// swaps to the tab bar.
struct WelcomeView: View {
    let onCreateAccount: () -> Void
    let onLogin: () -> Void

    @Environment(SupabaseService.self) private var supabase
    @State private var currentIndex: Int = 0
    @State private var autoAdvanceTick: Int = 0
    @State private var isSubmitting: Bool = false
    @State private var presentedLegalDoc: LegalDoc?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    init(onCreateAccount: @escaping () -> Void, onLogin: @escaping () -> Void) {
        self.onCreateAccount = onCreateAccount
        self.onLogin = onLogin

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
                .disabled(isSubmitting)

                Button {
                    onLogin()
                } label: {
                    Text("Log in")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                }
                .buttonStyle(.bordered)
                .disabled(isSubmitting)

                Button {
                    Task { await runApple() }
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "applelogo")
                            .font(.body)
                        Text("Continue with Apple")
                    }
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                }
                .buttonStyle(.borderedProminent)
                .tint(.black)
                .disabled(isSubmitting)

                Button {
                    Task { await runGoogle() }
                } label: {
                    HStack(spacing: 8) {
                        Image("GoogleGMark")
                            .resizable()
                            .scaledToFit()
                            .frame(width: 18, height: 18)
                        Text("Continue with Google")
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                }
                .buttonStyle(.bordered)
                .disabled(isSubmitting)

                if let error = supabase.authError {
                    Text(error)
                        .font(.footnote)
                        .foregroundStyle(.red)
                        .multilineTextAlignment(.center)
                        .padding(.top, 4)
                }

                Text("Read our [Terms](pebbles://legal/terms) and [Privacy](pebbles://legal/privacy) before creating an account with Apple or Google.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .tint(.accentColor)
                    .padding(.top, 8)
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
        .sheet(item: $presentedLegalDoc) { doc in
            LegalDocumentSheet(url: doc.url)
                .ignoresSafeArea()
        }
        .pebblesScreen()
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

#Preview {
    WelcomeView(
        onCreateAccount: {},
        onLogin: {}
    )
    .environment(SupabaseService())
}
