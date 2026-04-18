import SwiftUI

/// Renders a single `WelcomeStep` as left-aligned title + description.
/// The welcome carousel has no per-slide illustration — the Pebbles logo
/// in `WelcomeView`'s persistent header plays that role — so this view
/// stays intentionally minimal.
struct WelcomeSlideView: View {
    let step: WelcomeStep

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(step.title)
                .font(.title2.weight(.semibold))
                .foregroundStyle(Color.pebblesForeground)

            Text(step.description)
                .font(.body)
                .foregroundStyle(Color.pebblesMutedForeground)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 24)
    }
}

#Preview("Short") {
    WelcomeSlideView(step: WelcomeSteps.all[0])
}

#Preview("Longer") {
    WelcomeSlideView(step: WelcomeSteps.all[1])
}
