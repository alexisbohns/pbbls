import SwiftUI

/// A sheet's frame (the board's pick-3 register): the band (with the set's
/// ready mark when it is offered), the figure large, an optional lead, then
/// its sections, on the stock. Without its scroll, so the references draw it
/// whole; `AchievementSheetHost` scrolls it.
struct SheetBody<Figure: View, Sections: View>: View {
    let band: String
    var hint: String?
    var lead: String?
    var ready: ReadyMark?
    @ViewBuilder let figure: () -> Figure
    @ViewBuilder let sections: () -> Sections

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            AchievementBand(label: band, hint: hint, isHeading: true, ready: ready)
            figure()
                .frame(maxWidth: .infinity)
                .padding(.top, 16)
            if let lead {
                Text(verbatim: lead)
                    .spineType(.achievementsLead)
                    .foregroundStyle(Color.pebbles.onSurface)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 14)
            }
            sections()
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 34)
        .background(Color.pebbles.stock)
    }
}

/// A section's head (the board's `.lh`): the small-caps title, its italic note, a rule.
struct SheetSectionHead: View {
    let title: String
    var note: String?
    /// A family's name ink on the Emotions sheet.
    var tint: Color?

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            Text(verbatim: title)
                .spineType(.achievementsSectionTitle)
                .foregroundStyle(tint ?? Color.pebbles.onSurface)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: 0)
            if let note {
                Text(verbatim: note)
                    .spineType(.achievementsSectionNote)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(.bottom, 7)
        .overlay(alignment: .bottom) { Rectangle().fill(Color.pebbles.cardRule).frame(height: 1) }
    }
}

/// A section: its head, its rows, its "not yet" names, its tail or its quiet line.
struct SheetSectionView: View {
    let section: RegisterSection
    let lookups: WrapLookups
    var emotions: AchievementsPage.Emotions?
    var compact = false
    let open: (RegisterRowModel.Opens) -> Void

    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let inks = AchievementInks(scheme: scheme)
        let palette = section.family.flatMap(lookups.family)?.palette
        VStack(alignment: .leading, spacing: 0) {
            SheetSectionHead(title: section.title, note: section.note,
                             tint: section.family == nil ? nil : inks.labelInk(palette, family: section.family))
            ForEach(section.rows) { row in
                RegisterRow(row: row, lookups: lookups, emotions: emotions, compact: compact, open: open)
            }
            if !section.notYet.isEmpty { GhostNames(names: section.notYet) }
            if let trailing = section.trailing {
                Text(verbatim: trailing)
                    .spineType(.achievementsQuiet)
                    .foregroundStyle(inks.ink2)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.leading, 44)
                    .frame(maxWidth: .infinity, minHeight: SpineLayout.minTarget, alignment: .leading)
                    .overlay(alignment: .bottom) { Rectangle().fill(inks.rule).frame(height: 1) }
            }
            if let quiet = section.quiet {
                Text(verbatim: quiet)
                    .spineType(.achievementsQuiet)
                    .foregroundStyle(inks.ink2)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 10)
            }
        }
        .padding(.top, 28)
    }
}

/// A register row (the board's `.r`): 50 pt (44 in the glyph stacks), a button
/// when it opens a moment or a sheet, read by VoiceOver as one element.
struct RegisterRow: View {
    let row: RegisterRowModel
    let lookups: WrapLookups
    var emotions: AchievementsPage.Emotions?
    var compact = false
    let open: (RegisterRowModel.Opens) -> Void

    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        let locale = SpineStrings.appLocale
        if let opens = row.opens {
            Button { open(opens) } label: { content }
                .buttonStyle(.plain)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text(verbatim: row.accessibilityLabel))
                .accessibilityHint(Text(verbatim: SpineStrings.string(hintKey(opens), locale: locale)))
                .accessibilityAddTraits(.isButton)
        } else {
            content
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text(verbatim: row.accessibilityLabel))
        }
    }

    /// What tapping the row does, said after its label: Android's click labels.
    private func hintKey(_ opens: RegisterRowModel.Opens) -> String {
        switch opens {
        case .moment: "achievements.a11y.openPebble"
        case .sheet: "achievements.a11y.openSheet"
        }
    }

    private var content: some View {
        HStack(spacing: row.number == nil ? 10 : 8) {
            if let number = row.number {
                // One line at any text size: the number shrinks to its column rather than wrap.
                Text(verbatim: number)
                    .spineType(.achievementsRegisterNumber)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)
                    .frame(width: 42, alignment: .leading)
            }
            if row.mark != .none {
                RowMark(mark: row.mark, lookups: lookups, emotions: emotions)
                    .frame(width: row.number == nil ? 34 : 30)
            }
            VStack(alignment: .leading, spacing: 2) {
                name
                if let sub = row.sub {
                    Text(verbatim: sub)
                        .spineType(.achievementsRowSub)
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                        .fixedSize(horizontal: false, vertical: true)
                }
                if let next = row.next {
                    Text(verbatim: next)
                        .spineType(.achievementsRowNext)
                        .foregroundStyle(Color.pebbles.onSurface)
                        .fixedSize(horizontal: false, vertical: true)
                }
                // At accessibility sizes the date takes its own line rather than the row's end.
                if typeSize.isAccessibilitySize, row.isNew || row.date != nil { dateMark }
            }
            // The text takes the row's width, so a name or a line that wraps is measured where it is drawn.
            .frame(maxWidth: .infinity, alignment: .leading)
            .layoutPriority(1)
            if !typeSize.isAccessibilitySize { dateMark.fixedSize() }
            if case .sheet = row.opens {
                Image(systemName: "chevron.right")
                    .font(.system(size: 15, weight: .medium, design: .rounded))
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
            }
        }
        .padding(.vertical, 5)
        .frame(minHeight: compact ? SpineLayout.minTarget : 50)
        .overlay(alignment: .bottom) { Rectangle().fill(Color.pebbles.rule).frame(height: 1) }
        .contentShape(Rectangle())
    }

    /// The new dot and the date.
    private var dateMark: some View {
        HStack(spacing: 6) {
            if row.isNew { NewDot() }
            if let date = row.date {
                Text(verbatim: date)
                    .spineType(.achievementsRowDate)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .lineLimit(1)
            }
        }
    }

    @ViewBuilder
    private var name: some View {
        switch row.style {
        case .name:
            Text(verbatim: row.name)
                .spineType(.achievementsRowName)
                .foregroundStyle(Color.pebbles.onSurface)
                .fixedSize(horizontal: false, vertical: true)
        case .number:
            Text(verbatim: row.name)
                .spineType(compact ? .achievementsRowNumberSmall : .achievementsRowNumber)
                .foregroundStyle(Color.pebbles.onSurface)
        case .next:
            Text(verbatim: row.name)
                .spineType(compact ? .achievementsRowNumberSmall : .achievementsRowNumber)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
        case .ghostNumber:
            Text(verbatim: row.name)
                .spineType(compact ? .achievementsRowNumberSmall : .achievementsRowNumberGhost)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
        }
    }
}

/// A row's mark in its 28 pt box: the figures' own pieces, larger.
struct RowMark: View {
    let mark: RegisterRowModel.Mark
    let lookups: WrapLookups
    var emotions: AchievementsPage.Emotions?

    @Environment(\.colorScheme) private var scheme

    var body: some View {
        switch mark {
        case .rosace:
            if let emotions {
                EmotionRosace(emotions: emotions, lookups: lookups, side: 28, labelled: false, showsCentre: false,
                              showsMarks: false)
                    .frame(width: 28, height: 28)
            }
        case .stage(let index, let fill):
            SkylineStage(index: index, fill: fill)
        case .none:
            EmptyView()
        default:
            Canvas { context, size in
                draw(context, at: CGPoint(x: size.width / 2, y: size.height / 2))
            }
            .frame(width: 28, height: 28)
            .accessibilityHidden(true)
        }
    }

    private func draw(_ c: GraphicsContext, at centre: CGPoint) {
        let inks = AchievementInks(scheme: scheme)
        switch mark {
        case .firstFive(let task):
            c.drawFirstFiveMark(task, at: centre, scale: 1.7, inks: inks)
        case .sixFirst(let kind):
            let seal = FlatSeal.sixFirst(kind)
            c.draw(FlatSeal(size: seal.size, outline: seal.outline, print: seal.print, width: seal.width * 1.55),
                   at: centre, color: inks.ink, inks: inks)
        case .twentyFive:
            let seats = ExplorerWheelGeometry.twentyFiveSeats
            c.stroke(Path.circle(centre, 11), with: .color(inks.ink2), lineWidth: 1)
            for index in 0..<seats {
                let place = ExplorerWheelGeometry.point(11, ExplorerWheelGeometry.seatAngle(index, of: seats))
                c.fill(Path.circle(CGPoint(x: centre.x + place.x, y: centre.y + place.y), 1.1), with: .color(inks.ink))
            }
        case .pebble(let valence, let family):
            let palette = family.flatMap(lookups.family)?.palette
            c.draw(FlatSeal(valence: valence, width: 9 + CGFloat(valence.intensity) * 2.4), at: centre,
                   color: inks.sealInk(palette, polarity: valence.polarity), inks: inks)
        case .domain:
            c.draw(FlatSeal(size: .small, outline: .neutral, print: .solid, width: 17), at: centre, color: inks.ink,
                   inks: inks)
        case .ghost:
            c.drawGhost(at: centre, radius: 3.6, inks: inks)
        case .rosace, .stage, .none:
            break
        }
    }
}

/// The new dot (D10): 5 pt of ink. Its row says "new since you last looked".
struct NewDot: View {
    var body: some View {
        Circle()
            .fill(Color.pebbles.onSurface)
            .frame(width: 5, height: 5)
            .accessibilityHidden(true)
    }
}

/// "Not yet" as dotted names (the board's `.rest`), wrapping; read as one: "Not yet: …".
struct GhostNames: View {
    let names: [String]

    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let inks = AchievementInks(scheme: scheme)
        GhostFlow(spacing: 14, lineSpacing: 6) {
            ForEach(Array(names.enumerated()), id: \.offset) { _, name in
                HStack(spacing: 6) {
                    Canvas { c, size in
                        c.drawGhost(at: CGPoint(x: size.width / 2, y: size.height / 2), radius: 3.6, inks: inks)
                    }
                    .frame(width: 10, height: 10)
                    Text(verbatim: name)
                        .spineType(.achievementsGhostName)
                        .foregroundStyle(inks.ink2)
                }
            }
        }
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .overlay(alignment: .bottom) { Rectangle().fill(inks.rule).frame(height: 1) }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: AchievementsCopy.notYet(names, locale: SpineStrings.appLocale)))
    }
}

/// Leading-aligned lines of subviews, wrapping at the width offered.
struct GhostFlow: Layout {
    var spacing: CGFloat
    var lineSpacing: CGFloat

    private struct Item {
        let index: Int
        let x: CGFloat
        let size: CGSize
    }

    private struct Line {
        var items: [Item] = []
        var y: CGFloat = 0
        var height: CGFloat = 0
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let lines = arrange(proposal.width ?? .infinity, subviews)
        let widest = lines.map { line in line.items.last.map { $0.x + $0.size.width } ?? 0 }.max() ?? 0
        return CGSize(width: proposal.width ?? widest, height: lines.last.map { $0.y + $0.height } ?? 0)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        for line in arrange(bounds.width, subviews) {
            for item in line.items {
                subviews[item.index].place(at: CGPoint(x: bounds.minX + item.x, y: bounds.minY + line.y),
                                           proposal: ProposedViewSize(item.size))
            }
        }
    }

    private func arrange(_ maxWidth: CGFloat, _ subviews: Subviews) -> [Line] {
        var lines: [Line] = []
        var line = Line()
        var x: CGFloat = 0
        for index in subviews.indices {
            let size = subviews[index].sizeThatFits(.unspecified)
            if x > 0, x + size.width > maxWidth {
                lines.append(line)
                line = Line(y: line.y + line.height + lineSpacing)
                x = 0
            }
            line.items.append(Item(index: index, x: x, size: size))
            line.height = max(line.height, size.height)
            x += size.width + spacing
        }
        if !line.items.isEmpty { lines.append(line) }
        return lines
    }
}

/// The year table's column heads (the board's `.yth`): one line each. At
/// accessibility sizes the table stacks each month's name over its cells, and
/// the three columns share the row's width.
struct YearColumnsHead: View {
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        let locale = SpineStrings.appLocale
        let stacked = typeSize.isAccessibilitySize
        HStack(spacing: 6) {
            if !stacked { Spacer(minLength: 0) }
            ForEach(["moments", "pebbles", "weeks"], id: \.self) { key in
                Text(verbatim: AchievementsCopy.sheet("column.\(key)", locale: locale))
                    .spineType(.achievementsColumn)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)
                    .frame(maxWidth: stacked ? .infinity : 58)
            }
        }
        .padding(.top, 8)
        .padding(.bottom, 4)
        .overlay(alignment: .bottom) { Rectangle().fill(Color.pebbles.rule).frame(height: 1) }
        .accessibilityHidden(true)
    }
}

/// A month of the year table (the board's `.r.yt`): its counter, its name and
/// three gauges, read as one line; not a button. A month without pebbles dims.
/// The name is never shrunk or cut: at accessibility sizes every month puts it
/// on its own line above its cells.
struct YearRow: View {
    let row: YearRowModel
    let lookups: WrapLookups

    @Environment(\.colorScheme) private var scheme
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        let inks = AchievementInks(scheme: scheme)
        let hue = YearDisc.tone(row.month, lookups: lookups, inks: inks)
        let dim = row.month.isMuted ? 0.55 : 1
        Group {
            if typeSize.isAccessibilitySize {
                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 6) {
                        counter(hue, inks)
                        name(inks).opacity(dim)
                    }
                    HStack(spacing: 6) {
                        YearCellView(cell: row.moments, hue: nil, inks: inks, width: nil)
                        YearCellView(cell: row.pebbles, hue: hue, inks: inks, width: nil)
                        YearCellView(cell: row.weeks, hue: nil, inks: inks, width: nil)
                    }
                    .opacity(dim)
                }
                .padding(.vertical, 8)
            } else {
                HStack(spacing: 6) {
                    counter(hue, inks)
                    name(inks).opacity(dim)
                    Spacer(minLength: 4)
                    HStack(spacing: 6) {
                        YearCellView(cell: row.moments, hue: nil, inks: inks)
                        YearCellView(cell: row.pebbles, hue: hue, inks: inks)
                        YearCellView(cell: row.weeks, hue: nil, inks: inks)
                    }
                    .opacity(dim)
                }
            }
        }
        .frame(minHeight: 48)
        .overlay(alignment: .bottom) { Rectangle().fill(inks.rule).frame(height: 1) }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: row.accessibilityLabel))
    }

    private func counter(_ hue: Color?, _ inks: AchievementInks) -> some View {
        Canvas { c, size in
            c.drawMonthCounter(row.month, at: CGPoint(x: size.width / 2, y: size.height / 2), hue: hue, width: 14,
                               inks: inks)
        }
        .frame(width: 30, height: 28)
    }

    private func name(_ inks: AchievementInks) -> some View {
        Text(verbatim: row.name)
            .spineType(.achievementsRowName)
            .foregroundStyle(inks.ink)
            .fixedSize(horizontal: false, vertical: true)
    }
}

/// One gauge (the board's `cell`): the served number, in the done style when
/// the server says so, over a hairline and its fill (capped in the drawing).
/// `width` nil shares the row's width with its neighbours.
struct YearCellView: View {
    let cell: YearCell
    let hue: Color?
    let inks: AchievementInks
    var width: CGFloat? = 58

    var body: some View {
        let tone = cell.usesHue ? (hue ?? inks.ink) : inks.ink
        VStack(spacing: 4) {
            HStack(spacing: 0) {
                Text(verbatim: cell.value)
                    .spineType(cell.isDone ? .achievementsCellDone : .achievementsCell)
                    .foregroundStyle(cell.isDone ? tone : inks.ink2)
                if let target = cell.target {
                    Text(verbatim: target)
                        .spineType(.achievementsCell)
                        .foregroundStyle(inks.ink2)
                }
            }
            .lineLimit(1)
            GeometryReader { proxy in
                ZStack(alignment: .leading) {
                    Rectangle().fill(inks.ink2.opacity(0.55)).frame(height: 1)
                    if let fill = cell.fill, fill > 0 {
                        Capsule().fill(tone).frame(width: proxy.size.width * CGFloat(min(fill, 1)), height: 2.5)
                    }
                }
                .frame(maxHeight: .infinity)
            }
            .frame(height: 4)
            .padding(.horizontal, 3)
        }
        .frame(maxWidth: width ?? .infinity)
        .frame(width: width)
    }
}
