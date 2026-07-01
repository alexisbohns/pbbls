import SwiftUI

/// In-app "+N karma" flash. Drops from the top to echo the Dynamic Island on
/// devices without one. Same visual language as `PathBottomBar`'s karma stat
/// (sparkle + accent). Tap to dismiss; VoiceOver announces the earn.
struct KarmaEarnedCapsule: View {
    let content: KarmaEarnedContent
    let onTap: () -> Void

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: "sparkle")
                .foregroundStyle(Color.accent.primary)
            Text("+\(content.amount) karma")
                .font(.ysabeauSemibold(16))
                .foregroundStyle(Color.system.foreground)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(.regularMaterial, in: Capsule())
        .overlay(Capsule().strokeBorder(Color.accent.primary.opacity(0.2)))
        .shadow(color: .black.opacity(0.12), radius: 12, y: 4)
        .contentShape(Capsule())
        .onTapGesture(perform: onTap)
        .accessibilityElement()
        .accessibilityLabel("Earned \(content.amount) karma, \(String(localized: content.reason.label))")
        .accessibilityAddTraits(.isStaticText)
    }
}

/// Presents the capsule as a top overlay driven by `KarmaNotificationService`.
struct KarmaCapsuleOverlay: ViewModifier {
    @Environment(KarmaNotificationService.self) private var karma

    func body(content: Content) -> some View {
        content.overlay(alignment: .top) {
            if let earned = karma.activeCapsule {
                KarmaEarnedCapsule(content: earned) { karma.dismissCapsule() }
                    .padding(.top, 8)
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .zIndex(1)
            }
        }
        .animation(.spring(response: 0.4, dampingFraction: 0.75), value: karma.activeCapsule)
    }
}

extension View {
    func karmaCapsuleOverlay() -> some View { modifier(KarmaCapsuleOverlay()) }
}
