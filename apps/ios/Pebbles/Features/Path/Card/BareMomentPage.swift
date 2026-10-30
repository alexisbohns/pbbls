import SwiftUI
import os

/// A moment with no pebble has no card (#1146 §Bare page): the plain opening
/// (the kicker in small caps, the title, the photo on a stock mat, the words),
/// then the place its card will print into, a dashed card outline held by four
/// album corners, a dashed stone, "Not a pebble yet", one sentence, and **Make
/// it a pebble**, the page's one call to action. The place reports itself and
/// its dashed stone as `PrintAnchors` for the print, and its frame for the
/// scroll that shows it whole.
///
/// VoiceOver: the kicker, the title (a heading), the photo, the words, the
/// place's kicker and sentence, then the button.
struct BareMomentPage: View {
    let detail: MomentDetail
    let ratio: CGFloat?
    let onComplete: () -> Void
    var isCompleteDisabled = false
    /// The place's global frame, for `PrintStart`.
    var onPlaceFrame: (CGRect) -> Void = { _ in }
    /// The held card's VoiceOver landing on the title after the open (E4).
    var titleFocus: AccessibilityFocusState<CardFocus?>.Binding?

    static let placeID = "bare-place"

    private var locale: Locale { SpineStrings.appLocale }

    var body: some View {
        VStack(spacing: 0) {
            kicker
            Text(verbatim: detail.name.flatMap { $0.isEmpty ? nil : $0 } ?? SpineCopy.untitled(locale: locale))
                .spineType(.bareTitle)
                .foregroundStyle(Color.pebbles.onSurface)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 10)
                .accessibilityAddTraits(.isHeader)
                .cardFocus(titleFocus, .title)
            if let path = detail.snaps.first?.storagePath {
                BarePhoto(path: path, ratio: ratio ?? CardSizes.pendingRatio)
                    .padding(.top, 20)
            }
            if let words = detail.description, !words.isEmpty {
                Text(verbatim: words)
                    .spineType(.cardLeafBody)
                    .foregroundStyle(Color.pebbles.onSurface)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 16)
            }
            BarePlace(onComplete: onComplete, isDisabled: isCompleteDisabled)
                .id(Self.placeID)
                // Merged, not set: the place's dashed stone reports its own anchor beneath.
                .transformAnchorPreference(key: PrintAnchors.self, value: .bounds) { $0[.place] = $1 }
                .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { onPlaceFrame($0) }
                .padding(.top, 28)
        }
        .padding(.horizontal, CardLayout.pageMargin)
        .frame(maxWidth: CardLayout.maxWidth + 2 * CardLayout.pageMargin)
        .frame(maxWidth: .infinity)
    }

    private var kicker: some View {
        let parts = CardDateline.kicker(detail.happenedAt, calendar: SpineCalendar.current, locale: locale)
        return (Text(verbatim: parts.day)
            + Text(verbatim: " · ").foregroundStyle(Color.pebbles.outline)
            + Text(verbatim: parts.time).foregroundStyle(Color.pebbles.onSurface))
            .spineType(.kicker)
            .foregroundStyle(Color.pebbles.onSurfaceVariant)
            .multilineTextAlignment(.center)
            // A pause rather than the middle dot read out.
            .accessibilityLabel(Text(verbatim: "\(parts.day), \(parts.time)"))
    }
}

/// The photo on a stock mat, whole, at its ratio, no taller than the Spine's
/// tallest card less its chrome, so a portrait narrows rather than pushing the
/// place below the fold.
private struct BarePhoto: View {
    let path: String
    let ratio: CGFloat

    @Environment(\.spineSnapSource) private var snaps
    @State private var url: URL?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "held-card")

    var body: some View {
        let tallest = CardSizes.cap(3) - CardSizes.chromeVertical
        photo
            .aspectRatio(ratio, contentMode: .fit)
            .frame(maxWidth: tallest * ratio)
            .padding(10)
            .background(Color.pebbles.stock)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text("Photo"))
            .accessibilityAddTraits(.isImage)
            .task(id: path) {
                guard snaps.image(path) == nil else { return }
                url = await snaps.url(path)
            }
    }

    @ViewBuilder
    private var photo: some View {
        if let image = snaps.image(path) {
            Image(uiImage: image).resizable().scaledToFit()
        } else if let url {
            AsyncImage(url: url) { phase in
                switch phase {
                case .success(let image):
                    image.resizable().scaledToFit()
                case .failure(let error):
                    bed.onAppear {
                        logger.warning("bare page photo failed: \(error.localizedDescription, privacy: .private)")
                    }
                default:
                    bed
                }
            }
        } else {
            bed
        }
    }

    private var bed: some View {
        Rectangle().fill(Color.pebbles.halo)
    }
}

/// "Not a pebble yet": the place kept for the card, at least the card's
/// 1 : 1.12 and taller when its words need it (a fixed ratio would clip at a
/// large type size), and the one call to action.
private struct BarePlace: View {
    let onComplete: () -> Void
    let isDisabled: Bool

    var body: some View {
        MinimumRatio(ratio: 1.12) {
            VStack(spacing: 0) {
                GhostStone()
                    .anchorPreference(key: PrintAnchors.self, value: .bounds) { [.ghost: $0] }
                Text(CardStrings.placeKicker)
                    .spineType(.sectionLabel)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .padding(.top, 16)
                    .accessibilityAddTraits(.isHeader)
                Text(CardStrings.placeBody)
                    .spineType(.placeBody)
                    .foregroundStyle(Color.pebbles.onSurface)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 8)
                    .padding(.horizontal, 24)
                Button(action: onComplete) {
                    Label { Text("Make it a pebble") } icon: { PbblsIconImage(.plusCircle, .row) }
                }
                .buttonStyle(PebblesPrimaryButtonStyle())
                .disabled(isDisabled)
                .padding(.top, 22)
            }
            .padding(.vertical, 28)
            .padding(.horizontal, 20)
            .frame(maxWidth: .infinity)
        }
        .background { PlaceOutline() }
    }
}

/// The dashed card outline and its four album corners, clipped to the
/// outline's 16 pt rounding.
private struct PlaceOutline: View {
    var body: some View {
        Canvas { context, size in
            let rect = CGRect(origin: .zero, size: size)
            let rounded = RoundedRectangle(cornerRadius: 16, style: .continuous).path(in: rect)
            var corners = context
            corners.clip(to: rounded)
            let leg: CGFloat = 24
            let w = size.width, h = size.height
            for triangle in [[CGPoint(x: 0, y: 0), CGPoint(x: leg, y: 0), CGPoint(x: 0, y: leg)],
                             [CGPoint(x: w, y: 0), CGPoint(x: w, y: leg), CGPoint(x: w - leg, y: 0)],
                             [CGPoint(x: 0, y: h), CGPoint(x: 0, y: h - leg), CGPoint(x: leg, y: h)],
                             [CGPoint(x: w, y: h), CGPoint(x: w - leg, y: h), CGPoint(x: w, y: h - leg)]] {
                var path = Path()
                path.addLines(triangle)
                path.closeSubpath()
                corners.fill(path, with: .color(Color.pebbles.rule))
            }
            let inset = RoundedRectangle(cornerRadius: 15.5, style: .continuous)
                .path(in: rect.insetBy(dx: 0.5, dy: 0.5))
            context.stroke(inset, with: .color(Color.pebbles.outline),
                           style: StrokeStyle(lineWidth: 1, dash: [4, 4]))
        }
        .accessibilityHidden(true)
    }
}

/// The stone its card will carry, as a dashed outline: a medium neutral pebble,
/// whatever comes. The print flies the same outline to the verso's clearing.
struct GhostStone: View {
    var width: CGFloat = 64

    var body: some View {
        let stone = CardLayout.versoStoneSize(.medium)
        Canvas { context, size in
            let style = StrokeStyle(lineWidth: 1.25, dash: [4, 4])
            guard let outline = StoneRingGeometry.forStone(.neutralMedium)?.outline,
                  !outline.boundingRect.isEmpty else {
                context.stroke(Path(ellipseIn: CGRect(origin: .zero, size: size).insetBy(dx: 1, dy: 1)),
                               with: .color(Color.pebbles.outline), style: style)
                return
            }
            let box = outline.boundingRect
            let scale = min((size.width - 2) / box.width, (size.height - 2) / box.height)
            let fitted = outline.applying(CGAffineTransform(translationX: -box.minX, y: -box.minY)
                .concatenating(CGAffineTransform(scaleX: scale, y: scale))
                .concatenating(CGAffineTransform(translationX: 1, y: 1)))
            context.stroke(fitted, with: .color(Color.pebbles.outline), style: style)
        }
        .frame(width: width, height: width * stone.height / stone.width)
        .accessibilityHidden(true)
    }
}

/// At least `width / ratio` tall, and taller when the content needs it.
private struct MinimumRatio: Layout {
    let ratio: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        guard let child = subviews.first else { return .zero }
        let width = proposal.width ?? child.sizeThatFits(.unspecified).width
        let natural = child.sizeThatFits(ProposedViewSize(width: width, height: nil))
        return CGSize(width: width, height: max(natural.height, width / ratio))
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        subviews.first?.place(at: CGPoint(x: bounds.midX, y: bounds.midY), anchor: .center,
                              proposal: ProposedViewSize(width: bounds.width, height: nil))
    }
}
