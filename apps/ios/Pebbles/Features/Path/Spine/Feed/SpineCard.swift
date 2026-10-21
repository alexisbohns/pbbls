import SwiftUI
import os

/// The Deck card (#1130 R4–R5): the photo uncropped at its own ratio in a tinted
/// field with a 1.8u mat, the index (day and weekday) and the rail (time |
/// emotion) in the gutters, the title in its valence cut, the souls strip, the
/// painted edge and the shadow, on the stock with an 11 pt radius. Chrome is in
/// card units, `u = min(width / 70, 4.4)`; card text scales through
/// `SpineType`'s `.card(cap: 1.3)` (D11). Until the ratio is known the field is
/// square, then it eases to the ratio over 240 ms (snaps under Reduce Motion).
///
/// No button of its own: `SpinePebble` wraps it. It reports its global frame to
/// `CardFrameRegistry` for stack E and hides while E flies it.
struct SpineCard: View {
    let moment: MomentSummary
    /// The emotion's palette; nil for a bare moment or an unknown emotion, which
    /// wear the blank or brand ramp and paint no edge (Android's `SpineCardShell`).
    let emotion: EmotionPalette?
    /// The photo's ratio once probed; nil holds the square.
    let ratio: CGFloat?
    let column: CGFloat
    let soulNames: [String: String]
    let soulGlyphs: [String: [GlyphStroke]]
    /// The persons-of-the-week order, so the strip names people as the page does.
    let soulOrder: [String]
    /// Up to this many souls are named in full: 3 on the Spine (R7), 8 on the held card (E).
    var namedInFull = 3

    @Environment(\.colorScheme) private var scheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.spineSnapSource) private var snaps
    @Environment(CardFrameRegistry.self) private var registry: CardFrameRegistry?
    @State private var shownRatio: CGFloat?
    @State private var photoURL: URL?
    @State private var loggedFailure: URL?
    /// The frame this slot last reported; a reference, so writing it on every
    /// scroll frame invalidates nothing.
    @State private var reported = ReportedFrame()

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "spine")

    init(moment: MomentSummary, emotion: EmotionPalette?, ratio: CGFloat?, column: CGFloat,
         soulNames: [String: String], soulGlyphs: [String: [GlyphStroke]], soulOrder: [String], namedInFull: Int = 3) {
        self.moment = moment
        self.emotion = emotion
        self.ratio = ratio
        self.column = column
        self.soulNames = soulNames
        self.soulGlyphs = soulGlyphs
        self.soulOrder = soulOrder
        self.namedInFull = namedInFull
        // A ratio already known draws at once (and under ImageRenderer); a later one eases.
        _shownRatio = State(initialValue: ratio)
    }

    private var locale: Locale { SpineStrings.appLocale }
    private var calendar: Calendar { SpineCalendar.current }
    private var intensity: Int { moment.pebble?.intensity ?? 2 }
    private var valence: Valence { moment.pebble?.valence ?? .neutralMedium }
    private var palette: StonePalette { SpineColors.palette(emotion, isBlank: moment.pebble == nil) }
    private var spot: Color { SpineColors.spot(palette, scheme: scheme) }

    var body: some View {
        let size = CardSizes.cardSize(intensity: intensity, ratio: shownRatio, column: column)
        let u = min(size.width / 70, 4.4)
        VStack(spacing: 2.6 * u) {
            field(width: size.width, u: u)
            title(u: u)
            strip(u: u)
        }
        .padding(EdgeInsets(top: 4.6 * u, leading: 9 * u, bottom: 6.2 * u, trailing: 9 * u))
        .frame(width: size.width)
        .overlay(alignment: .topLeading) { index(u: u) }
        .overlay(alignment: .topTrailing) { rail(u: u) }
        .overlay {
            RoundedRectangle(cornerRadius: 1.3 * u)
                .strokeBorder(Color.pebbles.cardRule, lineWidth: 1)
                .padding(1.6 * u)
                .accessibilityHidden(true)
        }
        .background { ground }
        .opacity(registry?.hidden == moment.id ? 0 : 1)
        .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { frame in
            reported.frame = frame
            registry?.report(moment.id, frame: frame)
        }
        .onDisappear {
            guard let frame = reported.frame else { return }
            registry?.forget(moment.id, ifAt: frame)
            reported.frame = nil
        }
        .onChange(of: ratio) { _, ratio in
            guard let ratio, ratio != shownRatio else { return }
            if reduceMotion {
                shownRatio = ratio
            } else {
                withAnimation(.easeOut(duration: 0.24)) { shownRatio = ratio }
            }
        }
        .task(id: moment.firstSnapPath) {
            photoURL = nil
            guard let path = moment.firstSnapPath, snaps.image(path) == nil else { return }
            photoURL = await snaps.url(path)
        }
    }

    // MARK: - Field and photo

    private func field(width: CGFloat, u: CGFloat) -> some View {
        let photoWidth = width - 21.6 * u
        let photoHeight = photoWidth / (shownRatio ?? CardSizes.pendingRatio)
        return ZStack {
            SpineCardField(polarity: valence.polarity, spot: spot, tint: SpineColors.tintK(scheme: scheme),
                           pattern: SpineColors.patternK(scheme: scheme),
                           size: CGSize(width: photoWidth + 3.6 * u, height: photoHeight + 3.6 * u))
            photo(u: u).frame(width: photoWidth, height: photoHeight)
        }
        .accessibilityHidden(true)
    }

    /// Uncropped: `scaledToFit`, so a photo whose ratio is still pending sits
    /// letterboxed in the square rather than cut.
    @ViewBuilder
    private func photo(u: CGFloat) -> some View {
        if let path = moment.firstSnapPath, let image = snaps.image(path) {
            Image(uiImage: image).resizable().scaledToFit()
        } else if let photoURL {
            AsyncImage(url: photoURL) { phase in
                switch phase {
                case .success(let image):
                    image.resizable().scaledToFit()
                case .failure(let error):
                    unavailable(u: u).onAppear {
                        // Once per URL: a card scrolled back into view does not log again.
                        guard loggedFailure != photoURL else { return }
                        loggedFailure = photoURL
                        let reason = error.localizedDescription
                        Self.logger.warning("spine card photo failed: \(reason, privacy: .private)")
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
        Rectangle().fill(spot.opacity(0.18))
    }

    private func unavailable(u: CGFloat) -> some View {
        bed.overlay {
            Text(verbatim: SpineCopy.photoUnavailable(locale: locale))
                .spineType(.cardOthers(u: u))
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
        }
    }

    // MARK: - Title and strip

    @ViewBuilder
    private func title(u: CGFloat) -> some View {
        if let name = moment.name, !name.isEmpty {
            Text(verbatim: name)
                .spineType(.cardTitle(u: u, cut: EntryTypes.cardTitleCut(valence: valence.positiveness)))
                .foregroundStyle(Color.pebbles.onSurface)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
        } else {
            Text(verbatim: SpineCopy.untitled(locale: locale))
                .spineType(.cardTitle(u: u, cut: .untitled(size: 4.4)))
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
                .frame(maxWidth: .infinity)
        }
    }

    @ViewBuilder
    private func strip(u: CGFloat) -> some View {
        let line = PeopleLines.peopleLine(moment.soulIds, order: soulOrder, locale: locale, namedInFull: namedInFull)
        let others = SpineCopy.others(count: line.others, words: line.othersWords, locale: locale)
        let parts = PeopleLineParts.parts(line, names: names, with: "", others: others, style: .card)
        if !parts.isEmpty {
            PeopleLineView(parts: parts, glyphs: soulGlyphs, markSide: 4 * u, base: .cardOthers(u: u),
                           name: .cardSoul(u: u), aside: .cardOthers(u: u), more: .cardMore(u: u), alignment: .center)
                .frame(maxWidth: .infinity)
        }
    }

    /// The page's names, falling back to the names `path_moments` carries on the pebble.
    private var names: [String: String] {
        var names = soulNames
        for soul in moment.pebble?.souls ?? [] {
            let key = soul.id.uuidString.lowercased()
            names[key] = names[key] ?? soul.name
        }
        return names
    }

    // MARK: - Gutters and ground

    private func index(u: CGFloat) -> some View {
        VStack(spacing: 0.4 * u) {
            Text(verbatim: "\(calendar.component(.day, from: moment.happenedAt))")
                .spineType(.cardIndexNumeral(u: u))
                .foregroundStyle(Color.pebbles.onSurface)
            Text(verbatim: SpineDates.shortWeekday(moment.happenedAt, calendar: calendar, locale: locale))
                .spineType(.cardIndexDay(u: u))
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
        }
        .frame(width: 9 * u)
        .padding(.top, 4.6 * u)
        .accessibilityHidden(true)
    }

    private func rail(u: CGFloat) -> some View {
        QuarterTurn {
            HStack(spacing: 1.2 * u) {
                Text(verbatim: SpineDates.time(moment.happenedAt, calendar: calendar, locale: locale))
                    .spineType(.cardRail(u: u))
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                if let emotion = moment.pebble?.emotion {
                    Rectangle().fill(Color.pebbles.cardRule).frame(width: 1, height: 1.8 * u)
                    Text(verbatim: emotion.localizedName)
                        .spineType(.cardRailEmotion(u: u))
                        .foregroundStyle(spot)
                }
            }
            .fixedSize()
            .rotationEffect(.degrees(90))
        }
        .frame(width: 9 * u)
        .padding(.top, 4.6 * u)
        .accessibilityHidden(true)
    }

    /// Android's `SpineCardShell` order, bottom up: the shadow, the Night ring, the
    /// painted edge (2 / 3.5 / 5.5 pt, none without an emotion), the stock. On the
    /// ground only, so no text casts a shadow. Each shadow layer is the card's shape
    /// in `shadowInk` at its own alpha, inset by its negative spread (SwiftUI's
    /// `.shadow` has no spread), dropped by `dy` and blurred at the CSS σ = blur / 2,
    /// so the pool sits under the card instead of haloing its sides.
    private var ground: some View {
        let shape = RoundedRectangle(cornerRadius: 11, style: .continuous)
        let ink = SpineColors.shadowInk(scheme: scheme)
        let edge = SpineColors.edge(emotion, polarity: valence.polarity, scheme: scheme)
        return ZStack {
            ForEach(Array(SpineColors.cardShadow(scheme: scheme).enumerated()), id: \.offset) { _, layer in
                shape.fill(ink.opacity(layer.alpha))
                    .padding(-layer.spread)
                    .offset(y: layer.dy)
                    .blur(radius: layer.blur / 2)
            }
            if let ring = SpineColors.nightRing(scheme: scheme) {
                let half = SpineColors.nightRingWidth / 2
                RoundedRectangle(cornerRadius: 11 + half, style: .continuous)
                    .stroke(ring, lineWidth: SpineColors.nightRingWidth)
                    .padding(-half)
            }
            if let edge {
                shape.fill(edge).offset(y: CardSizes.edge(intensity))
            }
            shape.fill(Color.pebbles.stock)
        }
        .accessibilityHidden(true)
    }
}

/// Lays one child out a quarter turn: it reports the child's ideal size with
/// the sides swapped and centres the child, so the child's own
/// `.rotationEffect(.degrees(90))` lands inside the swapped box. Measured in
/// layout, so it holds under `ImageRenderer` too.
/// A slot's last reported frame, held by reference so updating it re-renders nothing.
private final class ReportedFrame {
    var frame: CGRect?
}

private struct QuarterTurn: Layout {
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        guard let child = subviews.first else { return .zero }
        let size = child.sizeThatFits(.unspecified)
        return CGSize(width: size.height, height: size.width)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        guard let child = subviews.first else { return }
        let size = child.sizeThatFits(.unspecified)
        child.place(at: CGPoint(x: bounds.midX, y: bounds.midY), anchor: .center, proposal: ProposedViewSize(size))
    }
}
