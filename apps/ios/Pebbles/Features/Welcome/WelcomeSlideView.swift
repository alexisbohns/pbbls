import SwiftUI

/// Renders a single `WelcomeStep` as a centered title + description.
/// The title uses the bundled Ysabeau-SemiBold display face in muted
/// foreground; the description keeps the system body font and is also
/// centered. The welcome carousel has no per-slide illustration — the
/// Pebbles logo in `WelcomeView`'s persistent header plays that role.
struct WelcomeSlideView: View {
    let step: WelcomeStep

    var body: some View {
        VStack(alignment: .center, spacing: 8) {
            Text(step.title)
                .font(.custom("Ysabeau-SemiBold", size: 22))
                .foregroundStyle(Color.pebblesMutedForeground)
                .multilineTextAlignment(.center)

            Text(step.description)
                .font(.body)
                .foregroundStyle(Color.pebblesMutedForeground)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity, alignment: .center)
        .padding(.horizontal, 24)
    }
}

#Preview("Short") {
    WelcomeSlideView(step: WelcomeSteps.all[0])
}

#Preview("Longer") {
    WelcomeSlideView(step: WelcomeSteps.all[1])
}
