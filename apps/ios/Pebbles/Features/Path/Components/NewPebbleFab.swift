import SwiftUI

/// Path's floating "New pebble" button (#903 §5): a circle floating above the
/// tab bar's trailing end. On iOS 26 it is Liquid Glass tinted with the
/// accent, and `placed()` centres it over the bar's trailing end cap, where
/// a search tab's circle would sit. Below 26 the tab bar is an edge-to-edge
/// strip and there is no glass, so it is a flat accent circle 16 pt from the
/// edge. A tap opens Capture. There is no long-press: none existed on iOS,
/// and Android's went with the all-at-once form (§1).
struct NewPebbleFab: View {
    let onTap: () -> Void

    private static let side: CGFloat = 56

    var body: some View {
        Button(action: onTap) {
            PbblsIconImage(.plusCircle, .tile, style: AnyShapeStyle(Color.pebbles.onPrimary))
                .frame(width: Self.side, height: Self.side)
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accentCircle()
        .accessibilityLabel("New pebble")
    }
}

extension NewPebbleFab {
    /// The iOS 26 tab bar floats `barMargin` in from the screen's sides and is
    /// `barHeight` tall, so its trailing end cap is centred `barMargin +
    /// barHeight / 2` from the trailing edge (measured on the iPhone 17 and
    /// 16e simulators, iOS 26.3). The button shares that centre, as Slack
    /// centres its + over the search circle.
    private static let barMargin: CGFloat = 21
    private static let barHeight: CGFloat = 62

    /// Pads the button into its spot: trailing, `gap` above the tab bar.
    func placed() -> some View {
        frame(maxWidth: .infinity, alignment: .trailing)
            .padding(.trailing, Self.trailingInset)
            .padding(.bottom, Self.gap)
    }

    private static var trailingInset: CGFloat {
        if #available(iOS 26.0, *) {
            barMargin + (barHeight - side) / 2
        } else {
            16
        }
    }

    private static var gap: CGFloat {
        if #available(iOS 26.0, *) { 16 } else { 8 }
    }
}

private extension View {
    /// The accent circle. Real `.glassEffect` on iOS 26, behind the same
    /// localized `if #available` exception as `KarmaEarnedCapsule` (Liquid
    /// Glass has no iOS 17 equivalent); a filled circle with a soft shadow
    /// below 26.
    @ViewBuilder
    func accentCircle() -> some View {
        if #available(iOS 26.0, *) {
            glassEffect(.regular.tint(Color.pebbles.primary).interactive(), in: Circle())
        } else {
            background(Circle().fill(Color.pebbles.primary))
                .shadow(color: Color.pebbles.scrim.opacity(0.16), radius: 12, y: 4)
        }
    }
}

#Preview {
    NewPebbleFab(onTap: {}).padding()
}
