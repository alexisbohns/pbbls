import SwiftUI

/// A brief, non-blocking line over the achievements page or a sheet (#1306):
/// a ceremony pull that failed says so, as Android's snackbar does, then goes.
/// VoiceOver hears it as an announcement. Styled as the app's moment toast.
struct AchievementsNotice: View {
    let text: String
    let onDismiss: () -> Void

    var body: some View {
        Text(verbatim: text)
            .pebblesFont(.callout)
            .foregroundStyle(Color.pebbles.onSurface)
            .fixedSize(horizontal: false, vertical: true)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.sm)
            .background {
                ZStack {
                    Rectangle().fill(.ultraThinMaterial)
                    Color.pebbles.surfaceContainer.opacity(0.85)
                }
                .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
            }
            .overlay {
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .strokeBorder(Color.pebbles.cardRule, lineWidth: 0.5)
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.bottom, Spacing.lg)
            .onTapGesture { onDismiss() }
            .accessibilityAddTraits(.isStaticText)
            .accessibilityAction(.escape) { onDismiss() }
            .task(id: text) {
                AccessibilityNotification.Announcement(text).post()
                try? await Task.sleep(for: .seconds(4))
                if !Task.isCancelled { onDismiss() }
            }
            .transition(.move(edge: .bottom).combined(with: .opacity))
    }
}

extension AchievementsCopy {
    /// What a pull says when it did not open the ceremony: only a failure speaks
    /// (no row, not ready and busy are quiet; the model logs them).
    static func pullNotice(_ outcome: AchievementsModel.PullOutcome, locale: Locale) -> String? {
        outcome == .failed ? SpineStrings.string("achievements.ceremony.error", locale: locale) : nil
    }
}
