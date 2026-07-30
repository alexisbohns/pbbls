import SwiftUI

/// The unlock moment (D13): one card per newly unlocked badge, chained in
/// catalog order and advanced by tapping. Hosted by the karma overlay window,
/// so it floats above whatever sheet fired it.
///
/// Dismissal is never blocking — tapping the scrim skips the rest of the queue.
struct AchievementMomentView: View {
    @Environment(AchievementNotificationService.self) private var achievements

    var body: some View {
        ZStack {
            // The scrim is a real subview, so the passthrough window lets it
            // capture touches for as long as the moment is up.
            Color.black.opacity(0.55)
                .ignoresSafeArea()
                .contentShape(Rectangle())
                .onTapGesture { achievements.dismiss() }
                .accessibilityHidden(true)

            if let card = achievements.currentCard {
                cardBody(card)
                    .padding(.horizontal, Spacing.xl)
                    .transition(.scale(scale: 0.92).combined(with: .opacity))
                    .id(card.id)
            }
        }
        .animation(.spring(response: 0.34, dampingFraction: 0.78), value: achievements.index)
    }

    @ViewBuilder
    private func cardBody(_ card: AchievementMomentCard) -> some View {
        VStack(spacing: Spacing.lg) {
            Image(systemName: "trophy")
                .font(.system(size: 44))
                .foregroundStyle(Color.accent.primary)
                .padding(Spacing.lg)
                .background(Circle().fill(Color.accent.primary.opacity(0.12)))

            VStack(spacing: Spacing.xs) {
                Text("Achievement unlocked")
                    .pebblesFont(.subhead)
                    .foregroundStyle(Color.system.secondary)
                Text(card.title)
                    .pebblesFont(.headlineEmphasized)
                    .foregroundStyle(Color.system.foreground)
                    .multilineTextAlignment(.center)
                if let description = card.description {
                    Text(description)
                        .pebblesFont(.subhead)
                        .foregroundStyle(Color.system.secondary)
                        .multilineTextAlignment(.center)
                }
            }

            if card.karmaGranted > 0 {
                Text("+\(card.karmaGranted) karma")
                    .pebblesFont(.headline)
                    .foregroundStyle(Color.accent.primary)
            }

            if achievements.cards.count > 1 {
                Text("\(achievements.index + 1) of \(achievements.cards.count)")
                    .pebblesFont(.subhead)
                    .foregroundStyle(Color.system.muted)
            }

            Button {
                achievements.advance()
            } label: {
                Text(achievements.isShowingLastCard ? "Nice" : "Next")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .tint(Color.accent.primary)
        }
        .padding(Spacing.xl)
        .frame(maxWidth: 340)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(Color.system.background)
        )
        .accessibilityElement(children: .contain)
        .accessibilityLabel(accessibilityLabel(card))
    }

    private func accessibilityLabel(_ card: AchievementMomentCard) -> String {
        var parts = [String(localized: "Achievement unlocked"), card.title]
        if card.karmaGranted > 0 {
            parts.append(String(format: NSLocalizedString(
                "achievement.moment.a11y.karma",
                value: "Earned %lld karma", comment: ""
            ), card.karmaGranted))
        }
        return parts.joined(separator: ", ")
    }
}

#Preview("One badge") {
    let service = AchievementNotificationService()
    service.present(cards: [
        AchievementMomentCard(
            id: "first-soul", title: "First soul",
            description: "Add your first soul.", karmaGranted: 5
        )
    ])
    return AchievementMomentView().environment(service)
}
