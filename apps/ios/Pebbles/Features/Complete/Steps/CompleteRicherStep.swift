import SwiftUI
import os

/// Make it richer (record-flow D12, Riverbed treatment), entered through the
/// stone reveal ceremony (#1691): the stone is set down large at the centre and
/// carved, held on Continue, then sent to its day in the week while the page
/// comes in under it: the name, "+N karma", the week, then three rows that each
/// edit the finished pebble: collection and visibility through `complete_pebble`
/// (keyed on moment and caller, so a second call is an edit), words through
/// `save_moment` (the edit sheet). Continue becomes Done in place (D6); Done
/// returns to the origin, Record another starts a fresh capture.
/// A high-distress pebble stays quiet: no karma line, a calm sentence and a
/// way to support instead (#1551).
struct CompleteRicherStep: View {
    let momentId: UUID
    let name: String?
    let happenedAt: Date?
    let result: CompletionResult
    let draft: CompleteDraft
    let onExit: () -> Void
    /// Nil when the host cannot start another capture (the held card).
    let onRecordAnother: (() -> Void)?
    let onEdited: () -> Void
    /// Reference renders only: ImageRenderer draws neither ScrollView content
    /// nor Menus, so `false` also draws the two menu rows as plain rows.
    var scrolls = true

    @Environment(EmotionPaletteService.self) private var palettes
    @Environment(ReferenceDataService.self) private var refs
    @Environment(SupabaseService.self) private var supabase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale

    @State private var collectionId: UUID?
    @State private var visibility: Visibility
    @State private var hasWords: Bool
    @State private var words: MomentDetail?
    @State private var editError: String?
    /// One edit at a time: the rows sit out while a write or the words fetch is in flight.
    @State private var isEditing = false
    @State private var showsCrisisResources = false
    /// The ceremony: built on the step's first appearance, or frozen in `init` (references, D8).
    @State private var driver: StoneCeremonyDriver?
    /// Act 1 plays once, from the stage's first appearance.
    @State private var hasStarted = false

    private var quiet: Bool { draft.valence?.quietSuccess ?? false }
    private var valence: Valence { draft.valence ?? .neutralMedium }

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "complete-richer")

    init(momentId: UUID, name: String?, happenedAt: Date?, result: CompletionResult, draft: CompleteDraft,
         hasWords: Bool, onExit: @escaping () -> Void, onRecordAnother: (() -> Void)?, onEdited: @escaping () -> Void,
         scrolls: Bool = true, frozen: StoneCeremonyFreeze? = nil) {
        self.momentId = momentId
        self.scrolls = scrolls
        self.name = name
        self.happenedAt = happenedAt
        self.result = result
        self.draft = draft
        self.onExit = onExit
        self.onRecordAnother = onRecordAnother
        self.onEdited = onEdited
        _collectionId = State(initialValue: draft.collectionId)
        _visibility = State(initialValue: draft.visibility)
        _hasWords = State(initialValue: hasWords)
        if let frozen {
            // Never starts a clock or a haptic. Every wave has ended by the pause, so no reach is needed.
            // The palette is the environment's, out of reach here; the carve does not depend on it.
            let stone = Self.stoneSpec(draft: draft, result: result, palette: .brand)
            let timeline = StoneCeremonyTimeline(valence: draft.valence ?? .neutralMedium,
                                                 carves: Self.carveLength(stone, scale: 2) > 0, mode: .full)
            let ceremony = StoneCeremony(timeline: timeline, markedDay: Self.markedDay(happenedAt),
                                         heroReach: [], frozen: frozen)
            _driver = State(initialValue: StoneCeremonyDriver(ceremony: ceremony, carveLength: 0, hapticsOn: false))
        }
    }

    /// The pebble's stone, built as `PebbleStoneView` builds it.
    private var spec: LitStoneSpec {
        Self.stoneSpec(draft: draft, result: result,
                       palette: draft.emotionId.flatMap { palettes.palette(for: $0) }.map(StonePalette.init(emotion:))
                        ?? .brand)
    }

    private static func stoneSpec(draft: CompleteDraft, result: CompletionResult,
                                  palette: StonePalette) -> LitStoneSpec {
        let valence = draft.valence ?? .neutralMedium
        return LitStoneSpec(size: valence.sizeGroup, polarity: valence.polarity, palette: palette,
                            glyph: result.glyph, seed: StableHash.seed(result.pebbleId), night: false, flat: false)
    }

    /// The ink's length at the hero size. One predicate on every path: a stone carves when it is above 0.
    private static func carveLength(_ spec: LitStoneSpec, scale: CGFloat) -> CGFloat {
        LitStonePainter.carveLength(spec, box: StoneCeremonyLayout.heroSize(spec.size), scale: scale)
    }

    private static func markedDay(_ happenedAt: Date?) -> Int {
        happenedAt.flatMap { RicherWeekRow.markedIndex(around: $0, calendar: SpineCalendar.current) } ?? 0
    }

    var body: some View {
        let running = driver?.ceremony.isRunning ?? false
        TimelineView(.animation(minimumInterval: nil, paused: !running)) { context in
            let ceremony = driver?.ceremony
            // Before the driver is built, every value is at its start: nothing shows.
            let frame = ceremony?.frame(at: context.date) ?? StoneCeremonyFrame()
            let accepts = ceremony?.acceptsRicherInput(at: context.date) ?? false
            GeometryReader { proxy in
                page(frame: frame, accepts: accepts, region: proxy.size)
                    .onAppear { buildDriver(width: proxy.size.width) }
            }
            .stoneCeremonyLandmarks { landmarks in
                stage(frame: frame, landmarks: landmarks)
            }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                bar(frame: frame, accepts: accepts)
            }
            .contentShape(Rectangle())
            // A tap that no control takes skips to the next rest; once still, the controls alone listen.
            .gesture(TapGesture().onEnded { driver?.tapOutside(at: .now) }, including: running ? .all : .subviews)
        }
        .task(id: driver?.ceremony.phase) { await tickAtEnd() }
        .onDisappear { driver?.close() }
        .sheet(isPresented: $showsCrisisResources) {
            CrisisResourcesSheet()
        }
        .sheet(item: $words) { detail in
            EditMomentSheet(detail: detail, onSaved: {
                hasWords = true
                onEdited()
            })
        }
    }

    // MARK: Ceremony

    /// Read once, here: the mode, the haptics switch, the carve's length at the hero size.
    private func buildDriver(width: CGFloat) {
        guard driver == nil else { return }
        let mode = StoneCeremonyMode.resolve(reduceMotion: reduceMotion,
                                             lowPower: ProcessInfo.processInfo.isLowPowerModeEnabled)
        let hero = StoneCeremonyLayout.heroSize(valence.sizeGroup)
        let carveLength = Self.carveLength(spec, scale: displayScale)
        let timeline = StoneCeremonyTimeline(valence: valence, carves: carveLength > 0, mode: mode)
        let heroReach = (0..<timeline.heroWaveCount).map {
            StoneCeremonyLayout.heroWaveReach(screenWidth: width, heroWidth: hero.width, wave: $0)
        }
        let ceremony = StoneCeremony(timeline: timeline, markedDay: Self.markedDay(happenedAt),
                                     heroReach: heroReach, start: .now)
        driver = StoneCeremonyDriver(ceremony: ceremony, carveLength: carveLength, hapticsOn: HapticsPreference.isOn())
    }

    /// Sleeps until the running phase ends, then moves the ceremony on, which stops the clock.
    private func tickAtEnd() async {
        guard let driver, let end = driver.ceremony.endsAt() else { return }
        let wait = end.timeIntervalSinceNow
        if wait > 0 {
            do {
                try await Task.sleep(for: .seconds(wait))
            } catch {
                return
            }
        }
        // Never a hair early: a tick before the end would leave the clock running.
        driver.tick(at: max(.now, end))
    }

    /// The stone over the page, placed from the column's centre slot and the day's cell.
    private func stage(frame: StoneCeremonyFrame, landmarks: StoneCeremonyLandmarks) -> some View {
        GeometryReader { space in
            if let driver, let centre = landmarks.centre {
                let centreRect = space[centre]
                StoneCeremonyStage(spec: spec, valence: valence, frame: frame, centre: centreRect,
                                   cell: landmarks.cell.map { space[$0] } ?? centreRect, bounds: space.size)
                    .onAppear {
                        guard !hasStarted else { return }
                        hasStarted = true
                        driver.start(at: .now)
                    }
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    // MARK: Page

    /// The page's top margin, over the column.
    private static let topInset = Spacing.lg

    @ViewBuilder
    private func page(frame: StoneCeremonyFrame, accepts: Bool, region: CGSize) -> some View {
        let column = content(frame: frame, accepts: accepts, region: region)
        if scrolls {
            // Still while the ceremony plays: the column places the hero's slot from the page's top.
            ScrollView { column }
                .scrollBounceBehavior(.basedOnSize)
                .scrollDisabled(!accepts)
        } else {
            column.frame(maxHeight: .infinity, alignment: .top)
        }
    }

    private func content(frame: StoneCeremonyFrame, accepts: Bool, region: CGSize) -> some View {
        let hero = StoneCeremonyLayout.heroSize(valence.sizeGroup)
        let landing = StoneCeremonyLayout.landingSize(valence.sizeGroup)
        return StoneCeremonyColumn(regionHeight: region.height, regionTop: Self.topInset, hero: hero,
                                   textDrop: frame.textDrop) {
            if happenedAt == nil {
                LitStoneImage(spec: spec)
                    .frame(width: landing.width, height: landing.height)
                    .opacity(frame.cellStoneOpacity)
                    .anchorPreference(key: StoneCeremonyCellKey.self, value: .bounds) { $0 }
                    .ceremonyColumn()
            }
            VStack(spacing: Spacing.sm) {
                MomentNameText(name: name, untitledFont: .ysabeauSemiboldItalic(28))
                    .pebblesFont(.title)
                    .multilineTextAlignment(.center)
                    .opacity(frame.nameOpacity)
                karmaLine.opacity(frame.karmaOpacity)
            }
            .ceremonyColumn(.text, gap: Spacing.sm)
            if let happenedAt {
                RicherWeekRow(date: happenedAt, stone: spec, dayOpacity: frame.dayOpacity,
                              stoneOpacity: frame.cellStoneOpacity)
                    .padding(.top, Spacing.sm)
                    .accessibilityHidden(!accepts)
                    .ceremonyColumn(gap: Spacing.sm)
            }
            richerRows(frame: frame, accepts: accepts)
                .ceremonyColumn(gap: Spacing.xl)
            // The hero's slot at the pause; it takes no room in the column.
            Color.clear
                .frame(width: hero.width, height: hero.height)
                .anchorPreference(key: StoneCeremonyCentreKey.self, value: .bounds) { $0 }
                .accessibilityHidden(true)
                .ceremonyColumn(.centre)
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.top, Self.topInset)
        .padding(.bottom, Spacing.lg)
    }

    /// The header and the rows, out of reach until the page is in.
    private func richerRows(frame: StoneCeremonyFrame, accepts: Bool) -> some View {
        // Index 0 is the header; each visible row takes the next.
        let showsCollections = !refs.collections.isEmpty
        let visibilityIndex = showsCollections ? 2 : 1
        return VStack(alignment: .leading, spacing: Spacing.sm) {
            Text("MAKE IT RICHER")
                .pebblesFont(.meta)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
                .cascade(frame, 0)
            Group {
                if showsCollections { collectionRow.cascade(frame, 1) }
                visibilityRow.cascade(frame, visibilityIndex)
                if !hasWords { wordsRow.cascade(frame, visibilityIndex + 1) }
            }
            .disabled(isEditing)
            if let editError {
                Text(verbatim: editError).pebblesFont(.subhead).foregroundStyle(Color.pebbles.error)
            }
        }
        .allowsHitTesting(accepts)
        .accessibilityHidden(!accepts)
    }

    @ViewBuilder
    private var karmaLine: some View {
        if quiet {
            Text("Thank you for keeping this. If today is heavy, support is one tap away.")
                .pebblesFont(.body)
                .foregroundStyle(Color.pebbles.onSurface)
                .multilineTextAlignment(.center)
        } else if let karma = result.karmaDelta, karma > 0 {
            HStack(spacing: 6) {
                PbblsGlyphView(.karmaDot, size: 18, relativeTo: .headline, style: AnyShapeStyle(Color.pebbles.primary))
                Text("+\(karma) karma").pebblesFont(.headline).foregroundStyle(Color.pebbles.onSurface)
            }
            .accessibilityElement(children: .combine)
            .accessibilityLabel(Text("Earned \(karma) karma"))
        }
    }

    // MARK: Bar

    /// One capsule from the pause to the end (D6): Continue, which becomes Done in place.
    /// A quiet pebble's Get support sits under it at the pause, then above Done; Record
    /// another takes the slot under it. Every spot is held, so nothing moves.
    private func bar(frame: StoneCeremonyFrame, accepts: Bool) -> some View {
        VStack(spacing: Spacing.xs) {
            if quiet {
                textButton("Get support") { showsCrisisResources = true }
                    .opacity(frame.actionsOpacity)
                    .allowsHitTesting(accepts)
                    .accessibilityHidden(!accepts)
            }
            Button(action: continueOrDone) {
                ZStack {
                    Text("Continue").opacity(1 - frame.buttonLabel)
                    Text("Done").opacity(frame.buttonLabel)
                }
            }
            .buttonStyle(PebblesPrimaryButtonStyle())
            .accessibilityLabel(accepts ? Text("Done") : Text("Continue"))
            .opacity(frame.buttonOpacity)
            // Nothing draws until Continue fades in, so VoiceOver meets it then.
            .accessibilityHidden(frame.buttonOpacity == 0)
            // Continue takes the tap from the first frame, before it is drawn: a transparent view is not
            // hit-tested, so this clear capsule behind it holds the spot (before contact it only skips).
            .background {
                Color.clear
                    .contentShape(Capsule())
                    .onTapGesture(perform: continueOrDone)
                    .accessibilityHidden(true)
            }
            ZStack {
                if let onRecordAnother {
                    textButton("Record another", action: onRecordAnother)
                        .opacity(frame.actionsOpacity)
                        .allowsHitTesting(accepts)
                        .accessibilityHidden(!accepts)
                }
                if quiet {
                    let shown = frame.supportUnderContinue > 0
                    textButton("Get support") { showsCrisisResources = true }
                        .opacity(frame.supportUnderContinue)
                        .allowsHitTesting(shown)
                        .accessibilityHidden(!shown)
                }
            }
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.vertical, Spacing.sm)
    }

    /// Continue until the page is in, which only lands it if Act 2 is already playing; then Done.
    private func continueOrDone() {
        guard let driver else { return }
        if driver.ceremony.acceptsRicherInput(at: .now) {
            onExit()
        } else {
            driver.tapContinue(at: .now)
        }
    }

    private func textButton(_ title: LocalizedStringKey, action: @escaping () -> Void) -> some View {
        Button(title, action: action)
            .pebblesFont(.bodyEmphasized)
            .foregroundStyle(Color.pebbles.primary)
            .frame(maxWidth: .infinity, minHeight: 44)
    }

    // MARK: Rows

    /// The icon set's tick, as a plain `Image` so a `Menu` item can carry it.
    private var tick: some View {
        Image(uiImage: GlyphCatalog.shared.image(.checkmarkDot, form: .regular, side: 24)).renderingMode(.template)
    }

    @ViewBuilder
    private var collectionRow: some View {
        let label = row(icon: .collection, title: "Add to a collection",
                        value: collectionId.flatMap { id in refs.collections.first { $0.id == id }?.name })
        if !scrolls {
            label
        } else {
            Menu {
                ForEach(refs.collections) { collection in
                    Button {
                        Task { await edit(collectionId: collection.id) }
                    } label: {
                        Label { Text(verbatim: collection.name) } icon: {
                            if collection.id == collectionId { tick }
                        }
                    }
                }
                if collectionId != nil {
                    Button("Remove from collection") { Task { await edit(collectionId: nil) } }
                }
            } label: { label }
        }
    }

    @ViewBuilder
    private var visibilityRow: some View {
        let label = row(icon: .abstractKey, title: "Who can see it", value: String(localized: visibility.label))
        if !scrolls {
            label
        } else {
            Menu {
                ForEach(Visibility.allCases) { grade in
                    Button {
                        Task { await edit(visibility: grade) }
                    } label: {
                        Label { Text(grade.label) } icon: { if grade == visibility { tick } }
                    }
                }
            } label: { label }
        }
    }

    private var wordsRow: some View {
        Button {
            Task { await openWords() }
        } label: {
            row(icon: .abstractPen, title: "Add a few words", value: String(localized: "+1 karma"))
        }
        .buttonStyle(.plain)
    }

    private func row(icon: PbblsGlyph, title: LocalizedStringResource, value: String?) -> some View {
        HStack(spacing: Spacing.md) {
            PbblsGlyphView(icon, size: 24, style: AnyShapeStyle(Color.pebbles.onSurfaceVariant))
            Text(title).pebblesFont(.body).foregroundStyle(Color.pebbles.onSurface)
            Spacer(minLength: Spacing.sm)
            if let value {
                Text(verbatim: value).pebblesFont(.subhead).foregroundStyle(Color.pebbles.onSurfaceVariant).lineLimit(1)
            }
            Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundStyle(Color.pebbles.outline)
        }
        .padding(.horizontal, Spacing.md)
        .frame(minHeight: 52)
        .frame(maxWidth: .infinity)
        .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(Color.pebbles.surfaceContainerLow))
        .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    // MARK: Edits (one write per tap, D6)

    private func edit(collectionId newId: UUID?) async {
        var payload = CompletePebblePayload(momentId: momentId)
        payload.collectionIds = newId.map { [$0] } ?? []
        await send(payload) { collectionId = newId }
    }

    private func edit(visibility grade: Visibility) async {
        var payload = CompletePebblePayload(momentId: momentId)
        payload.visibility = grade
        await send(payload) { visibility = grade }
    }

    private func send(_ payload: CompletePebblePayload, then apply: () -> Void) async {
        editError = nil
        isEditing = true
        defer { isEditing = false }
        do {
            _ = try await MomentsService(client: supabase.client).completePebble(payload)
            TapHaptics.play(.selection)
            apply()
            onEdited()
        } catch {
            logger.error("richer edit failed: \(error.localizedDescription, privacy: .private)")
            editError = userMessageForMomentWriteError(error)
        }
    }

    private func openWords() async {
        editError = nil
        isEditing = true
        defer { isEditing = false }
        do {
            words = try await MomentsService(client: supabase.client).moment(id: momentId)
        } catch {
            logger.error("moment(id:) for words failed: \(error.localizedDescription, privacy: .private)")
            editError = String(localized: "Something went wrong. Please try again.")
        }
    }
}

/// The week around the pebble's day, Monday first, the new stone on its day.
///
/// With a `stone` (the stone reveal ceremony, #1691) every slot is as tall as
/// the landed stone, so nothing shifts when it lands, and the marked slot draws
/// the stone instead of the dot. `dayOpacity` fades whole day columns, one value
/// per column; nil leaves them all at 1.
struct RicherWeekRow: View {
    struct Day: Identifiable {
        let date: Date
        let isMarked: Bool
        var id: Date { date }
    }

    let date: Date
    var stone: LitStoneSpec?
    var dayOpacity: [Double]?
    var stoneOpacity: Double

    init(date: Date, stone: LitStoneSpec? = nil, dayOpacity: [Double]? = nil, stoneOpacity: Double = 1) {
        self.date = date
        self.stone = stone
        self.dayOpacity = dayOpacity
        self.stoneOpacity = stoneOpacity
    }

    private var locale: Locale { SpineStrings.appLocale }

    static func days(around date: Date, calendar: Calendar) -> [Day] {
        let weekday = calendar.component(.weekday, from: date)
        let offsetToMonday = (weekday - calendar.firstWeekday + 7) % 7
        let start = calendar.startOfDay(for: calendar.date(byAdding: .day, value: -offsetToMonday, to: date) ?? date)
        return (0..<7).compactMap { offset in
            guard let day = calendar.date(byAdding: .day, value: offset, to: start) else { return nil }
            return Day(date: day, isMarked: calendar.isDate(day, inSameDayAs: date))
        }
    }

    /// Which of the seven columns holds the pebble's day: where the stone lands.
    static func markedIndex(around date: Date, calendar: Calendar) -> Int? {
        days(around: date, calendar: calendar).firstIndex(where: \.isMarked)
    }

    var body: some View {
        let days = Self.days(around: date, calendar: SpineCalendar.current)
        let slotHeight = StoneCeremonyLayout.slotHeight(stone?.size)
        HStack(spacing: Spacing.sm) {
            ForEach(Array(days.enumerated()), id: \.element.id) { index, day in
                VStack(spacing: Spacing.xs) {
                    Text(verbatim: day.date.formatted(.dateTime.weekday(.narrow).locale(locale)))
                        .pebblesFont(.meta)
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    slot(for: day, height: slotHeight)
                }
                .frame(maxWidth: .infinity)
                .opacity(dayOpacity?[index] ?? 1)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: date.formatted(.dateTime.weekday(.wide).day().month().locale(locale))))
    }

    @ViewBuilder
    private func slot(for day: Day, height: CGFloat) -> some View {
        if let stone, day.isMarked {
            let size = StoneCeremonyLayout.landingSize(stone.size)
            LitStoneImage(spec: stone)
                .frame(width: size.width, height: size.height)
                .opacity(stoneOpacity)
                .anchorPreference(key: StoneCeremonyCellKey.self, value: .bounds) { $0 }
                .frame(height: height)
        } else {
            Circle()
                .fill(day.isMarked ? Color.pebbles.primary : Color.pebbles.outlineVariant)
                .frame(width: day.isMarked ? 10 : 6, height: day.isMarked ? 10 : 6)
                .frame(height: height)
        }
    }
}

private extension View {
    /// The richer cascade (#1691): row `index` fades in with a 6 pt rise.
    func cascade(_ frame: StoneCeremonyFrame, _ index: Int) -> some View {
        opacity(frame.rowOpacity[index]).offset(y: frame.rowRise[index])
    }
}
