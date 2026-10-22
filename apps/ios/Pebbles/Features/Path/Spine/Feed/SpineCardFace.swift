import SwiftUI
import os

/// The card's print (#1130 R4–R5, moved out of `SpineCard` for #1146): the
/// photo uncropped at its ratio in a tinted field with a 1.8u mat, the index
/// (day and weekday) and the rail (time | emotion) in the gutters, the title in
/// its valence cut, the souls strip and the inset rule, in card units `u`.
/// `ratio` is the ratio to draw (nil holds the square); the caller eases it.
/// The Spine passes `u = min(width / 70, 4.4)`, the held card `width / 70`.
struct SpineCardFace: View {
    let moment: MomentSummary
    let palette: StonePalette
    let ratio: CGFloat?
    let width: CGFloat
    let u: CGFloat
    let soulNames: [String: String]
    let soulGlyphs: [String: [GlyphStroke]]
    /// The persons-of-the-week order, so the strip names people as the page does.
    let soulOrder: [String]
    /// Up to this many souls are named in full: 3 on the Spine (R7), 8 on the held card (E).
    var namedInFull = 3
    /// The held card's recto: VoiceOver reads the face in the card's order (§VoiceOver).
    var accessible = false
    /// Stands in the field's mat in place of the photo (the held card's F3) and
    /// carries the name, so the legend does not repeat it.
    var slip: AnyView?
    /// Lays out under the legend, across the printed border (the held card's words).
    var leaf: AnyView?
    /// The held card's VoiceOver landing on the title; nil on the Spine.
    var titleFocus: AccessibilityFocusState<CardFocus?>.Binding?

    @Environment(\.colorScheme) private var scheme
    @Environment(\.spineSnapSource) private var snaps
    /// The photo this face fetched itself, by path; the shared cache is read first.
    @State private var fetched: (path: String, image: UIImage)?
    @State private var failedURL: URL?

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "spine")

    /// Copy reads the app's language; the index's weekday and the rail's time
    /// add the device's hour cycle and region (`SpineStrings.dateLocale`, D3).
    private var locale: Locale { SpineStrings.appLocale }
    private var dateLocale: Locale { SpineStrings.dateLocale }
    private var calendar: Calendar { SpineCalendar.current }
    private var valence: Valence { moment.pebble?.valence ?? .neutralMedium }
    private var spot: Color { SpineColors.spot(palette, scheme: scheme) }

    var body: some View {
        VStack(spacing: 0) {
            VStack(spacing: 2.6 * u) {
                field(width: width, u: u)
                if slip == nil { title(u: u) }
                strip(u: u)
            }
            // Over a leaf, the legend's foot is the score's head room (4.4u).
            .padding(EdgeInsets(top: 4.6 * u, leading: 9 * u, bottom: leaf == nil ? 6.2 * u : 4.4 * u, trailing: 9 * u))
            .frame(width: width)
            .overlay(alignment: .topLeading) { index(u: u) }
            .overlay(alignment: .topTrailing) { rail(u: u) }
            if let leaf {
                // Inside the printed border, 1.6u in, so the score runs from rule to rule.
                leaf.padding(.horizontal, 1.6 * u)
            }
        }
        .frame(width: width)
        .overlay {
            RoundedRectangle(cornerRadius: 1.3 * u)
                .strokeBorder(Color.pebbles.cardRule, lineWidth: 1)
                .padding(1.6 * u)
                .accessibilityHidden(true)
        }
        .task(id: moment.firstSnapPath) { await loadPhoto() }
    }

    // MARK: - The photo's load

    /// A decoded photo at once: the source's (references, the lab), the shared
    /// cache's (a card the Spine already drew), or this face's own fetch.
    private func decoded(_ path: String) -> UIImage? {
        snaps.image(path) ?? SnapImageCache.shared.image(for: path) ?? (fetched?.path == path ? fetched?.image : nil)
    }

    /// Fetches the signed original once per path and caches the decoded image,
    /// so the held card flying out of this slot draws it with no fetch and no
    /// empty frame. Nothing is reset while the path is unchanged.
    private func loadPhoto() async {
        guard let path = moment.firstSnapPath, decoded(path) == nil else { return }
        // Unsigned: the cache logged why; the bed stays.
        guard let url = await snaps.url(path) else { return }
        do {
            let (data, _) = try await URLSession.shared.data(from: url)
            guard let image = await UIImage(data: data)?.byPreparingForDisplay() else {
                throw URLError(.cannotDecodeContentData)
            }
            // A decoded image is kept even when this card has scrolled off: the cache is shared.
            SnapImageCache.shared.store(image, for: path)
            guard !Task.isCancelled else { return }
            fetched = (path, image)
        } catch {
            guard !Task.isCancelled else { return }
            // Once per URL: a card scrolled back into view does not log again.
            if failedURL != url {
                let reason = error.localizedDescription
                Self.logger.warning("spine card photo failed: \(reason, privacy: .private)")
            }
            failedURL = url
        }
    }

    // MARK: - Field and photo

    @ViewBuilder
    private func field(width: CGFloat, u: CGFloat) -> some View {
        if let slip {
            // The mat round a slip is wider than round a photo (2.6u).
            slip
                .padding(2.6 * u)
                .frame(width: width - 18 * u)
                .background {
                    GeometryReader { proxy in
                        SpineCardField(polarity: valence.polarity, spot: spot, tint: SpineColors.tintK(scheme: scheme),
                                       pattern: SpineColors.patternK(scheme: scheme), size: proxy.size)
                    }
                    .accessibilityHidden(true)
                }
        } else if accessible {
            photoField(width: width, u: u)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text("Photo"))
                .accessibilityAddTraits(.isImage)
                .accessibilitySortPriority(2)
        } else {
            photoField(width: width, u: u)
                .accessibilityHidden(true)
        }
    }

    private func photoField(width: CGFloat, u: CGFloat) -> some View {
        let photoWidth = width - 21.6 * u
        let photoHeight = photoWidth / (ratio ?? CardSizes.pendingRatio)
        return ZStack {
            SpineCardField(polarity: valence.polarity, spot: spot, tint: SpineColors.tintK(scheme: scheme),
                           pattern: SpineColors.patternK(scheme: scheme),
                           size: CGSize(width: photoWidth + 3.6 * u, height: photoHeight + 3.6 * u))
            photo(u: u).frame(width: photoWidth, height: photoHeight)
        }
    }

    /// Uncropped: `scaledToFit`, so a photo whose ratio is still pending sits
    /// letterboxed in the square rather than cut.
    @ViewBuilder
    private func photo(u: CGFloat) -> some View {
        if let path = moment.firstSnapPath, let image = decoded(path) {
            Image(uiImage: image).resizable().scaledToFit()
        } else if failedURL != nil {
            unavailable(u: u)
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
                .accessibilityAddTraits(.isHeader)
                .accessibilitySortPriority(5)
                .cardFocus(titleFocus, .title)
        } else {
            Text(verbatim: SpineCopy.untitled(locale: locale))
                .spineType(.cardTitle(u: u, cut: .untitled(size: 4.4)))
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
                .frame(maxWidth: .infinity)
                .accessibilityAddTraits(.isHeader)
                .accessibilitySortPriority(5)
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
                // Everyone, once, as the locale lists them: one stop, not name by name.
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text(verbatim: everyone))
                .accessibilitySortPriority(3)
        }
    }

    private var everyone: String {
        moment.soulIds.compactMap { names[$0] }.formatted(.list(type: .and).locale(locale))
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

    // MARK: - Gutters

    private func index(u: CGFloat) -> some View {
        VStack(spacing: 0.4 * u) {
            Text(verbatim: "\(calendar.component(.day, from: moment.happenedAt))")
                .spineType(.cardIndexNumeral(u: u))
                .foregroundStyle(Color.pebbles.onSurface)
            Text(verbatim: SpineDates.shortWeekday(moment.happenedAt, calendar: calendar, locale: dateLocale))
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
                Text(verbatim: SpineDates.time(moment.happenedAt, calendar: calendar, locale: dateLocale))
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
}

/// Lays one child out a quarter turn: it reports the child's ideal size with
/// the sides swapped and centres the child, so the child's own
/// `.rotationEffect(.degrees(90))` lands inside the swapped box. Measured in
/// layout, so it holds under `ImageRenderer` too.
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
