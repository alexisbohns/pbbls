import SwiftUI

/// The photo-less recto's slip (#1146 §Recto, F3): a stock slip on the
/// emotion's mat carrying the name as the Spine's entry sets it, at 6.4 card
/// units, with the spot's foot line under it. It is the face's heading.
struct CardSlip: View {
    let name: String
    let cut: SpineType.Cut
    let spot: Color
    let u: CGFloat

    var body: some View {
        Text(verbatim: name)
            .spineType(.cardSlipName(u: u, cut: cut))
            .foregroundStyle(Color.pebbles.onSurface)
            .multilineTextAlignment(.center)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.vertical, 5 * u)
            .padding(.horizontal, 4 * u)
            .frame(maxWidth: .infinity, minHeight: 26 * u)
            .background(Color.pebbles.stock)
            .overlay(alignment: .bottom) {
                Rectangle()
                    .fill(spot.opacity(CardColors.slipFootAlpha))
                    .frame(height: 1)
                    .offset(y: 1)
                    .accessibilityHidden(true)
            }
            .accessibilityAddTraits(.isHeader)
            .accessibilitySortPriority(5)
    }
}
