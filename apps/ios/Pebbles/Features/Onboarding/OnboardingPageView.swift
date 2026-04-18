import SwiftUI

/// Renders a single `OnboardingStep`. Layout: large illustration card
/// at the top, bold title, body description. Switches on the image
/// enum — local asset vs remote URL — without leaking that distinction
/// to the parent.
struct OnboardingPageView: View {
    let step: OnboardingStep

    var body: some View {
        VStack(spacing: 32) {
            illustration
                .frame(maxWidth: .infinity)
                .frame(height: 360)
                .background(Color.pebblesSurfaceAlt)
                .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))

            VStack(alignment: .leading, spacing: 12) {
                Text(step.title)
                    .font(.title2.weight(.semibold))
                    .foregroundStyle(Color.pebblesForeground)

                Text(step.description)
                    .font(.body)
                    .foregroundStyle(Color.pebblesMutedForeground)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            Spacer(minLength: 0)
        }
        .padding(.horizontal, 24)
        .padding(.top, 24)
    }

    @ViewBuilder
    private var illustration: some View {
        switch step.image {
        case .asset(let name):
            Image(name)
                .resizable()
                .scaledToFit()
                .padding(24)

        case .remote(let url):
            AsyncImage(url: url) { phase in
                switch phase {
                case .success(let image):
                    image.resizable().scaledToFit().padding(24)
                default:
                    Color.pebblesSurfaceAlt
                }
            }
        }
    }
}

#Preview("Asset") {
    OnboardingPageView(step: OnboardingSteps.all[0])
}

#Preview("Long copy") {
    OnboardingPageView(step: OnboardingSteps.all[2])
}
