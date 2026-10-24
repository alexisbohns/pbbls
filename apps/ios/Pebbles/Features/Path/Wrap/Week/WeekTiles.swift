import SwiftUI

/// The wrap of the week (#1226, D4): a row of small tiles, the ones the server
/// listed in `wrap.tiles`, in its order, scrolling sideways. A tap opens the
/// week's sheet on that tile's list. Android: `WeekTiles`.
struct WeekTiles: View {
    let wrap: WeekWrap
    let page: SpinePage
    let lookups: WrapLookups
    let onOpen: (WeekTile) -> Void

    var body: some View {
        ScrollView(.horizontal) {
            WeekTileRow(wrap: wrap, page: page, lookups: lookups, onOpen: onOpen)
        }
        .scrollIndicators(.hidden)
        // Only a row wider than the page scrolls, so a short one leaves the swipe to the week pager.
        .scrollBounceBehavior(.basedOnSize)
    }
}

/// The tiles themselves, laid out in a row, every tile the tallest one's
/// height. The live week's tiles are dashed and read *so far*. Each tile is one
/// button whose label says its list in a sentence and whose hint says it opens
/// the list. Its own view, so the references draw it without a scroll view.
struct WeekTileRow: View {
    let wrap: WeekWrap
    let page: SpinePage
    let lookups: WrapLookups
    let onOpen: (WeekTile) -> Void

    @Environment(\.colorScheme) private var scheme

    private var locale: Locale { SpineStrings.appLocale }

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            ForEach(wrap.tiles, id: \.self) { tile in
                TileCard(dashed: wrap.isLive) { onOpen(tile) } content: {
                    head(tile)
                    tileBody(tile)
                        .padding(.top, 6)
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text(verbatim: WrapCopy.tileLabel(tile, body: sentence(tile), locale: locale)))
                .accessibilityHint(Text(verbatim: WrapCopy.tileHint(locale: locale)))
                .accessibilityAddTraits(.isButton)
                .accessibilityShowsLargeContentViewer {
                    Text(verbatim: WrapCopy.tileLabel(tile, body: sentence(tile), locale: locale))
                }
            }
        }
        // A tile's width is fixed (D11): past the first accessibility size its
        // text stops growing, the large content viewer and the sheet's lists
        // carrying the rest.
        .dynamicTypeSize(...DynamicTypeSize.accessibility1)
        // Every tile takes the tallest one's height, so the row reads as a row.
        .fixedSize(horizontal: false, vertical: true)
        .padding(.horizontal, SpineLayout.versoPadX)
        .padding(.top, 12)
        .padding(.bottom, 16)
    }

    // MARK: - Head and sentence

    /// The label, then the count or *so far* on its line when it fits, under it when it does not (French).
    private func head(_ tile: WeekTile) -> some View {
        let label = TileHeader(label: WrapCopy.tile(tile, locale: locale))
        return ViewThatFits(in: .horizontal) {
            HStack(alignment: .firstTextBaseline) {
                label
                Spacer(minLength: 4)
                headTrailing(tile)
            }
            VStack(alignment: .leading, spacing: 0) {
                label
                headTrailing(tile)
            }
        }
    }

    @ViewBuilder
    private func headTrailing(_ tile: WeekTile) -> some View {
        if wrap.isLive {
            Text(verbatim: WrapCopy.soFar(locale: locale))
                .spineType(.sectionHint)
                .foregroundStyle(Color.pebbles.outline)
                .lineLimit(1)
        } else if let count = WeekTileWords.count(tile, wrap: wrap) {
            Text(verbatim: "\(count)")
                .spineType(.contentsTime)
                .foregroundStyle(Color.pebbles.onSurface)
        }
    }

    private func sentence(_ tile: WeekTile) -> String {
        WeekTileWords.sentence(tile, wrap: wrap, soulNames: page.soulNames, lookups: lookups,
                               calendar: SpineCalendar.current, locale: locale, dateLocale: SpineStrings.dateLocale)
    }

    private var soulIds: [String] { (wrap.persons ?? []).map(\.soulId) }

    private var hourRange: (from: Double, to: Double)? { WeekTileWords.hourRange(wrap) }

    // MARK: - Bodies

    @ViewBuilder
    private func tileBody(_ tile: WeekTile) -> some View {
        switch tile {
        case .contents: contentsBody
        case .persons: personsBody
        case .emotions: emotionsBody
        case .domains: domainsBody
        case .weight: weightBody
        case .hours: hoursBody
        case .kept: keptBody
        }
    }

    /// A one-line caption shrinks a little rather than lose its end.
    private func caption(_ text: String, italic: Bool = false, lines: Int = 2) -> some View {
        Text(verbatim: text)
            .spineType(italic ? .tileCaptionItalic : .tileCaption)
            .foregroundStyle(Color.pebbles.onSurfaceVariant)
            .lineLimit(lines)
            .minimumScaleFactor(lines == 1 ? 0.8 : 1)
            .fixedSize(horizontal: false, vertical: true)
    }

    /// Seven weekday columns, a pip per moment (its seal's bare silhouette), at most four a day.
    private var contentsBody: some View {
        let letters = WeekOpening.weekdayLetters(weekStart: page.weekStart, calendar: SpineCalendar.current,
                                                 locale: locale)
        return HStack(alignment: .top, spacing: 0) {
            ForEach(0..<7, id: \.self) { index in
                VStack(spacing: 2) {
                    Text(verbatim: letters.indices.contains(index) ? letters[index] : "")
                        .spineType(.dayLetter)
                        .foregroundStyle(Color.pebbles.outline)
                    ForEach(moments(on: index).prefix(4), id: \.id) { moment in
                        StoneSeal(moment: moment, palette: SpineColors.palette(lookups.palette(moment),
                                                                               isBlank: moment.pebble == nil),
                                  size: .pip)
                    }
                }
                .frame(maxWidth: .infinity)
            }
        }
    }

    private func moments(on index: Int) -> [MomentSummary] {
        page.week.days.first { $0.index == index }?.moments ?? []
    }

    @ViewBuilder
    private var personsBody: some View {
        let ids = soulIds.filter { page.soulNames[$0] != nil }
        if ids.isEmpty {
            caption(sentence(.persons), italic: true, lines: 4)
        } else {
            HStack(spacing: 2) {
                ForEach(ids.prefix(5), id: \.self) { id in
                    SoulMark(strokes: page.soulGlyphs[id], side: 18)
                }
            }
            caption(sentence(.persons))
                .padding(.top, 6)
        }
    }

    private var emotionsBody: some View {
        VStack(alignment: .leading, spacing: 6) {
            ProportionBar(segments: (wrap.families ?? []).map { family in
                BarSegment(weight: family.share, color: WrapInk.spot(lookups.family(family.slug)?.palette,
                                                                     scheme: scheme))
            })
            caption(sentence(.emotions), italic: true)
        }
    }

    private var domainsBody: some View {
        VStack(alignment: .leading, spacing: 2) {
            ForEach(namedDomains.prefix(3), id: \.stat.domainId) { row in
                HStack(spacing: 4) {
                    Text(verbatim: row.name)
                        .spineType(.tileCaption)
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                        .lineLimit(1)
                    Text(verbatim: "\(row.stat.count)")
                        .spineType(.contentsTime)
                        .foregroundStyle(Color.pebbles.outline)
                }
            }
        }
    }

    /// The domains the device knows, with their names, in the server's order.
    private var namedDomains: [(stat: DomainStat, name: String)] {
        (wrap.domains ?? []).compactMap { stat in lookups.domain(stat.domainId).map { (stat, $0.localizedName) } }
    }

    /// Bright, even and heavy as three medium seals in the week's lead family, each with its count.
    @ViewBuilder
    private var weightBody: some View {
        if let weight = wrap.weight {
            let palette = (wrap.lead?.order.first).flatMap { lookups.family($0)?.palette }
            HStack(alignment: .bottom, spacing: 0) {
                ForEach(ValencePolarity.allCases.reversed(), id: \.self) { polarity in
                    VStack(spacing: 2) {
                        StoneSeal(valence: .of(.medium, polarity), glyph: nil,
                                  palette: SpineColors.palette(palette, isBlank: false), isBlank: false,
                                  size: .contents)
                        Text(verbatim: "\(weight.counts(polarity).reduce(0, +))")
                            .spineType(.contentsTime)
                            .foregroundStyle(Color.pebbles.onSurface)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            caption(WrapCopy.weightCaption(locale: locale), lines: 1)
                .padding(.top, 4)
        }
    }

    /// The day's 24 hours as a track, a dot in the pebble's ink where each happened.
    @ViewBuilder
    private var hoursBody: some View {
        let hours = wrap.hours ?? []
        if let range = hourRange {
            Canvas { context, size in
                let dot: CGFloat = 6
                for mark in hours {
                    let ink = WrapInk.spot(lookups.moment(mark.momentId).flatMap(lookups.palette), scheme: scheme)
                    let x = CGFloat(mark.happened / 24) * (size.width - dot)
                    context.fill(Path(ellipseIn: CGRect(x: x, y: (size.height - dot) / 2, width: dot, height: dot)),
                                 with: .color(ink))
                }
            }
            .frame(height: 14)
            Rectangle().fill(Color.pebbles.rule).frame(height: SpineLayout.rule)
            HStack {
                ForEach(["0", "12", "24"], id: \.self) { label in
                    Text(verbatim: label)
                        .spineType(.dayLetter)
                        .foregroundStyle(Color.pebbles.outline)
                    if label != "24" { Spacer(minLength: 0) }
                }
            }
            .padding(.top, 2)
            caption(WrapCopy.hoursRange(from: range.from, to: range.to, calendar: SpineCalendar.current,
                                        locale: locale, dateLocale: SpineStrings.dateLocale), lines: 1)
                .padding(.top, 6)
        }
    }

    @ViewBuilder
    private var keptBody: some View {
        if let kept = wrap.kept {
            caption(WrapCopy.photos(kept.photoMomentIds.count, locale: locale), lines: 1)
            caption(WrapCopy.cards(kept.cards.reduce(0) { $0 + $1.count }, locale: locale))
        }
    }
}

/// A family's ink as the seals print it (Android: `spotInk`): `primary` on Paper,
/// `secondary` at Night; grey without one.
enum WrapInk {
    static func spot(_ palette: EmotionPalette?, scheme: ColorScheme) -> Color {
        guard let palette else { return Color.pebbles.outline }
        return SpineColors.spot(StonePalette(emotion: palette), scheme: scheme)
    }
}

extension Valence {
    /// The valence of a size and a polarity.
    static func of(_ size: ValenceSizeGroup, _ polarity: ValencePolarity) -> Valence {
        allCases.first { $0.sizeGroup == size && $0.polarity == polarity } ?? .neutralMedium
    }
}

extension WeightGrid {
    /// The counts by intensity 1…3 of one polarity.
    func counts(_ polarity: ValencePolarity) -> [Int] {
        switch polarity {
        case .highlight: return bright
        case .neutral: return even
        case .lowlight: return heavy
        }
    }
}

/// What a week tile counts and says (#1226; Android: `tileCount`, `tileSentence`):
/// pure, so the tests read it without a view.
enum WeekTileWords {
    static func count(_ tile: WeekTile, wrap: WeekWrap) -> Int? {
        switch tile {
        case .contents: return wrap.pebbles
        case .persons: return (wrap.persons?.count).flatMap { $0 > 0 ? $0 : nil }
        case .emotions: return wrap.families?.count
        case .domains: return wrap.domains?.count
        case .weight, .hours, .kept: return nil
        }
    }

    // swiftlint:disable function_parameter_count
    /// What a tile says, for VoiceOver and, where it shows one, its caption.
    static func sentence(_ tile: WeekTile, wrap: WeekWrap, soulNames: [String: String], lookups: WrapLookups,
                         calendar: Calendar, locale: Locale, dateLocale: Locale) -> String {
        switch tile {
        case .contents:
            return "\(wrap.pebbles)"
        case .persons:
            let names = (wrap.persons ?? []).compactMap { soulNames[$0.soulId] }
            return names.isEmpty ? WrapCopy.invitation(lookups.inviteNames(wrap), long: false, locale: locale)
                : names.joined(separator: ", ")
        case .emotions:
            return WrapCopy.emotionsLead((wrap.lead?.order ?? []).map(lookups.familyName), locale: locale)
        case .domains:
            return WrapCopy.domainsLead(lookups.domainNames(wrap.domains ?? []), locale: locale)
        case .weight:
            guard let weight = wrap.weight else { return "" }
            return WrapCopy.weightLead(bright: weight.bright.reduce(0, +), even: weight.even.reduce(0, +),
                                       heavy: weight.heavy.reduce(0, +), locale: locale)
        case .hours:
            guard let range = hourRange(wrap) else { return "" }
            return WrapCopy.hoursLead(from: range.from, to: range.to, calendar: calendar, locale: locale,
                                      dateLocale: dateLocale)
        case .kept:
            guard let kept = wrap.kept else { return "" }
            return WrapCopy.photos(kept.photoMomentIds.count, locale: locale) + ", "
                + WrapCopy.cards(kept.cards.reduce(0) { $0 + $1.count }, locale: locale)
        }
    }

    // swiftlint:enable function_parameter_count

    /// The earliest and the latest hour a pebble happened at.
    static func hourRange(_ wrap: WeekWrap) -> (from: Double, to: Double)? {
        let hours = (wrap.hours ?? []).map(\.happened)
        guard let from = hours.min(), let to = hours.max() else { return nil }
        return (from, to)
    }
}
