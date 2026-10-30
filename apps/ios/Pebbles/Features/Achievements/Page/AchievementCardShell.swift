import SwiftUI

/// A card whose set is whole and offered (D9): its ceremony can be pulled.
struct ReadyMark {
    let stack: AchievementsPage.CeremonyStack
    let isPulling: Bool
    let pull: () -> Void
}

/// The achievements card (#1306, the board's pick-6 `gal`): the Spine's card
/// ground, a 34 pt margin with the card's title set bottom to top over its
/// ladder, then a band (small-caps title, italic hint, a rule) over the figure.
/// With an action the card is one button whose label is assembled from the
/// payload; a ready mark is a separate 44 pt button in the band, so VoiceOver
/// reaches both.
struct AchievementCardShell<Figure: View>: View {
    /// The margin's title, and the band's unless `band` names it.
    let title: String
    var band: String?
    var hint: String?
    var ladder: [LadderBar] = []
    /// One long bar instead of the ladder (Domains).
    var longLadder: LadderBar?
    let accessibilityLabel: String
    var ready: ReadyMark?
    var action: (() -> Void)?
    @ViewBuilder let figure: () -> Figure

    var body: some View {
        SpineCardShell(emotion: nil, polarity: .neutral, intensity: 1) {
            ZStack(alignment: .topTrailing) {
                main
                if let ready { ReadyButton(mark: ready).padding(.trailing, 4) }
            }
        }
        .padding(.horizontal, 14)
    }

    @ViewBuilder
    private var main: some View {
        if let action {
            Button(action: action) { content }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(verbatim: accessibilityLabel))
        } else {
            content
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text(verbatim: accessibilityLabel))
        }
    }

    private var content: some View {
        HStack(alignment: .top, spacing: 0) {
            VStack(spacing: 14) {
                Upright {
                    Text(verbatim: title)
                        .spineType(.achievementsMarginTitle)
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                        .lineLimit(1)
                        .fixedSize()
                        .rotationEffect(.degrees(-90))
                }
                Spacer(minLength: 0)
                if let longLadder {
                    MarginLadder(bars: [longLadder], barHeight: 120)
                } else {
                    MarginLadder(bars: ladder)
                }
            }
            .padding(.top, 16)
            .padding(.bottom, 14)
            .frame(width: 34)
            .frame(maxHeight: .infinity)
            .overlay(alignment: .trailing) {
                Rectangle().fill(Color.pebbles.cardRule).frame(width: 1)
            }
            VStack(alignment: .leading, spacing: 0) {
                AchievementBand(label: band ?? title, hint: ready == nil ? hint : nil)
                figure()
                    .frame(maxWidth: .infinity)
            }
            .padding(.horizontal, 14)
            .padding(.bottom, 16)
        }
        .fixedSize(horizontal: false, vertical: true)
        .frame(minHeight: SpineLayout.minTarget)
        .contentShape(Rectangle())
    }
}

/// A band (the board's `.band`): the small-caps title, the italic hint at the
/// far end, a rule under both. When the two cannot share a line (large text),
/// the hint goes under the title rather than being cut.
struct AchievementBand: View {
    let label: String
    var hint: String?
    /// A sheet's own title is a VoiceOver heading; a page card's band is part of its button.
    var isHeading = false
    /// A sheet's ready mark, in the hint's place: laid out with the title, so at
    /// large text it drops under it rather than overlapping it.
    var ready: ReadyMark?

    var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack(alignment: .center, spacing: 8) {
                title
                Spacer(minLength: 0)
                end
            }
            VStack(alignment: .leading, spacing: 4) {
                title
                end
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.top, 14)
        .padding(.bottom, 9)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Color.pebbles.cardRule).frame(height: 1)
        }
    }

    @ViewBuilder
    private var end: some View {
        if let ready {
            ReadyButton(mark: ready)
        } else if let hint {
            hintText(hint)
        }
    }

    private var title: some View {
        Text(verbatim: label)
            .spineType(.achievementsBand)
            .foregroundStyle(Color.pebbles.onSurface)
            .fixedSize(horizontal: false, vertical: true)
            .accessibilityAddTraits(isHeading ? .isHeader : [])
    }

    private func hintText(_ hint: String) -> some View {
        Text(verbatim: hint)
            .spineType(.achievementsBandHint)
            .foregroundStyle(Color.pebbles.onSurfaceVariant)
            .fixedSize(horizontal: false, vertical: true)
    }
}

/// The ready mark (D9): a quiet ring with its dot and "ready". It pulls the
/// ceremony; while the pull runs it cannot be tapped again.
struct ReadyButton: View {
    let mark: ReadyMark

    var body: some View {
        let locale = SpineStrings.appLocale
        Button(action: mark.pull) {
            HStack(spacing: 6) {
                ZStack {
                    Circle().strokeBorder(Color.pebbles.onSurface, lineWidth: 1).frame(width: 12, height: 12)
                    Circle().fill(Color.pebbles.onSurface).frame(width: 4, height: 4)
                }
                Text(verbatim: AchievementsCopy.ready(locale: locale))
                    .spineType(.achievementsBandHint)
                    .foregroundStyle(Color.pebbles.onSurface)
            }
            .padding(.horizontal, 10)
            .frame(minWidth: SpineLayout.minTarget, minHeight: SpineLayout.minTarget)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(mark.isPulling)
        .opacity(mark.isPulling ? 0.5 : 1)
        .accessibilityLabel(Text(verbatim: AchievementsA11y.ready(mark.stack, locale: locale)))
    }
}

/// Lays its one subview out a quarter turn round: as wide as it is tall and as
/// tall as it is wide, centred, so a `rotationEffect` sits inside its bounds.
struct Upright: Layout {
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        guard let first = subviews.first else { return .zero }
        let size = first.sizeThatFits(.unspecified)
        return CGSize(width: size.height, height: size.width)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        guard let first = subviews.first else { return }
        first.place(at: CGPoint(x: bounds.midX, y: bounds.midY), anchor: .center,
                    proposal: ProposedViewSize(first.sizeThatFits(.unspecified)))
    }
}
