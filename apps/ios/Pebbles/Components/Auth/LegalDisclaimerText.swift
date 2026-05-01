import SwiftUI

/// "Read our Terms and Privacy before creating an account…" disclaimer with
/// two tappable inline links. Lifted from the welcome screen so both Welcome
/// and Auth flows present the same legal copy.
struct LegalDisclaimerText: View {
    var onTermsTap: () -> Void
    var onPrivacyTap: () -> Void

    var body: some View {
        Text("Read our [Terms](pebbles://legal/terms) and [Privacy](pebbles://legal/privacy) before creating an account with Apple or Google.")
            .font(.caption)
            .foregroundStyle(Color.pebblesMutedForeground)
            .multilineTextAlignment(.center)
            .fixedSize(horizontal: false, vertical: true)
            .tint(Color.pebblesAccent)
            .environment(\.openURL, OpenURLAction { url in
                switch url.absoluteString {
                case "pebbles://legal/terms":
                    onTermsTap()
                    return .handled
                case "pebbles://legal/privacy":
                    onPrivacyTap()
                    return .handled
                default:
                    return .systemAction
                }
            })
    }
}

#Preview {
    LegalDisclaimerText(onTermsTap: {}, onPrivacyTap: {})
        .padding()
        .background(Color.pebblesBackground)
}
