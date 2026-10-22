import SwiftUI
import os

/// The held card's page (#1146 D2): its own top bar over the card in a
/// vertical `ScrollView`. One view serves the interim sheet (E1–E3) and the
/// overlay (E4). It loads the moment in `.task`, reloads it in place after an
/// edit, a pebble delete or a completion, and presents the edit sheets, the
/// Complete flow and the delete dialog over the window as before. A moment with
/// no pebble opens as the bare page, and a completion prints its card into it
/// (E3, D6); a restore mid-print lands the card at rest, verso up (D7).
struct HeldCardPage: View {
    let request: HeldCardRequest
    /// The card's resting global rect, for the host's FLIP; reported on every change.
    var onRest: (CGRect) -> Void = { _ in }
    let onClose: () -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(EmotionPaletteService.self) private var palettes
    @Environment(ReferenceDataService.self) private var refs
    @Environment(CompleteDraftStore.self) private var completeDrafts
    @Environment(MomentsRevision.self) private var revision
    @Environment(HeldCardHostModel.self) private var host
    @Environment(SnapAspectCache.self) private var aspects

    @State private var model: HeldCardModel
    @State private var turn: CardTurnModel
    @State private var pageWidth: CGFloat = ReferencePhone.width
    @State private var isPresentingEditMoment = false
    @State private var isPresentingEditPebble = false
    @State private var isPresentingComplete = false
    @State private var pendingDeletion: MomentDeleteTarget?
    @State private var deleteError: String?
    /// When the print started; nil when none runs (D6). Cleared only by its last frame.
    @State private var printStart: Date?
    @State private var printCrossfades = false
    @State private var placeFrame: CGRect?
    @State private var viewport: CGRect?
    /// The verso stone's press, played at 0.45 and kept for its own length.
    @State private var versoPress: StonePress?
    @AccessibilityFocusState private var focus: CardFocus?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    init(request: HeldCardRequest, onRest: @escaping (CGRect) -> Void = { _ in }, onClose: @escaping () -> Void) {
        self.request = request
        self.onRest = onRest
        self.onClose = onClose
        _model = State(initialValue: HeldCardModel(momentId: request.momentId, origin: request.origin,
                                                   onChanged: request.onChanged))
        _turn = State(initialValue: CardTurnModel(face: request.face))
    }

    private var service: MomentsService { MomentsService(client: supabase.client) }

    var body: some View {
        VStack(spacing: 0) {
            CardTopBar(shareURL: shareURL, menuItems: menuItems,
                       isDisabled: model.detail == nil || model.isDeleting || printStart != nil, onBack: onClose)
                .opacity(host.chrome)
            content
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { pageWidth = $0 }
        .onChange(of: turn.face, initial: true) { _, face in host.faceCommitted(face) }
        // After the open: the title on the recto, "Your pebble" on the verso.
        .onChange(of: host.phase == .open && model.detail?.pebble != nil, initial: true) { _, ready in
            if ready { focus = turn.face == .recto ? .title : .versoHeading }
        }
        .task {
            await reload()
            cutShortIfRestored()
        }
        .task(id: model.announcing) {
            guard model.announcing else { return }
            AccessibilityNotification.Announcement(String(localized: CardStrings.versoHeading)).post()
            try? await Task.sleep(for: .seconds(1))
            model.announced()
        }
        .task(id: model.detail?.snaps.first?.storagePath) {
            guard let path = model.detail?.snaps.first?.storagePath else { return }
            await aspects.probe(path)
        }
        .onChange(of: turn.landing) { _, face in host.land(face) }
        .interactiveDismissDisabled(model.isDeleting)
        .sheet(isPresented: $isPresentingEditPebble) {
            if let detail = model.detail, let pebble = detail.pebble {
                EditPebbleSheet(momentId: detail.id, pebble: pebble, onSaved: saved)
            }
        }
        .sheet(isPresented: $isPresentingEditMoment) {
            if let detail = model.detail {
                EditMomentSheet(detail: detail, onSaved: saved)
            }
        }
        .fullScreenCover(isPresented: $isPresentingComplete) {
            CompleteFlowView(momentId: request.momentId, momentName: model.detail?.name, onCompleted: { _ in
                // In place, behind the success screen.
                saved()
            })
        }
        .momentDeleteDialog(target: $pendingDeletion) { target, option in
            Task { await delete(option, on: target) }
        }
        .alert(
            "Couldn't delete",
            isPresented: Binding(get: { deleteError != nil }, set: { if !$0 { deleteError = nil } }),
            presenting: deleteError
        ) { _ in
            Button("OK", role: .cancel) { deleteError = nil }
        } message: { message in
            Text(message)
        }
    }

    @ViewBuilder
    private var content: some View {
        switch model.phase {
        case .loading:
            ScrollView {
                Group {
                    if let seed = request.seed, let pebble = seed.pebble {
                        seededCard(seed, pebble)
                    } else {
                        CardPlaceholder(width: CardLayout.cardWidth(available: pageWidth))
                    }
                }
                .modifier(Flying(host: host, onRest: onRest))
                .frame(maxWidth: .infinity)
                .padding(.top, 8)
            }
            .scrollClipDisabled()
        case .failed:
            VStack(spacing: 12) {
                Text("Couldn't load this pebble.")
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                Button("Retry") { Task { await reload(); cutShortIfRestored() } }
            }
        case .loaded:
            if let detail = model.detail {
                ScrollViewReader { proxy in
                    ScrollView {
                        page(detail)
                            .padding(.top, 8)
                            .padding(.bottom, 32)
                    }
                    .scrollClipDisabled()
                    .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { viewport = $0 }
                    .task(id: printReady) {
                        guard printReady else { return }
                        await beginPrint(proxy)
                    }
                }
            }
        }
    }

    /// The bare page until a completion has printed; the print while it runs;
    /// the card at rest otherwise.
    @ViewBuilder
    private func page(_ detail: MomentDetail) -> some View {
        if let pebble = detail.pebble, let printStart {
            TimelineView(.animation) { context in
                let duration = printCrossfades ? CompletionMotion.crossfade : CompletionMotion.duration
                let elapsed = context.date.timeIntervalSince(printStart)
                let t = CompletionMotion.timeline(elapsed: elapsed, duration: duration)
                CardPrint(t: t, crossfade: printCrossfades, page: bare(detail), card: card(detail, pebble, print: t))
                    .onChange(of: t >= CompletionMotion.pressAt, initial: true) { _, pressed in
                        if pressed, !printCrossfades { versoPress = .next(after: nil, still: false, now: .now) }
                    }
                    // The frame that reaches the end ends it (D6); `initial` covers a
                    // first frame already past the end after a late cover dismissal.
                    .onChange(of: t >= 1, initial: true) { _, done in
                        if done { endPrint() }
                    }
            }
        } else if let pebble = detail.pebble, model.completion == nil {
            card(detail, pebble, print: nil)
        } else {
            bare(detail)
        }
    }

    private func bare(_ detail: MomentDetail) -> BareMomentPage {
        BareMomentPage(detail: detail, ratio: detail.snaps.first.flatMap { aspects.ratio(for: $0.storagePath) },
                       onComplete: { isPresentingComplete = true },
                       isCompleteDisabled: model.completion != nil || printStart != nil,
                       onPlaceFrame: { placeFrame = $0 })
    }

    private func card(_ detail: MomentDetail, _ pebble: MomentDetail.Pebble, print: Double?) -> some View {
        PebbleCard(detail: detail, pebble: pebble, palette: palettes.palette(for: pebble.emotion.id),
                   ratio: detail.snaps.first.flatMap { aspects.ratio(for: $0.storagePath) },
                   width: CardLayout.cardWidth(available: pageWidth), domains: refs.domains,
                   collectionGlyphs: collectionGlyphs, soulGlyphs: soulGlyphs, soulOrder: request.soulOrder,
                   turn: turn, print: print, stonePress: versoPress, focus: $focus)
            .modifier(Flying(host: host, onRest: onRest, active: print == nil))
            .frame(maxWidth: .infinity)
            .stonePressLifetime($versoPress)
    }

    /// The seed's recto (#1146 §Flight, first frame): the Spine card at the
    /// card's width, so the flight lands on a card, never a spinner; the leaf,
    /// the souls past three and the verso fill in when the moment lands.
    private func seededCard(_ seed: MomentSummary, _ pebble: MomentSummary.PebbleSummary) -> some View {
        let width = CardLayout.cardWidth(available: pageWidth)
        let emotion = pebble.emotion.flatMap { palettes.palette(for: $0.id) }
        return SpineCardShell(emotion: emotion, polarity: pebble.valence.polarity, intensity: pebble.intensity) {
            SpineCardFace(moment: seed, palette: SpineColors.palette(emotion, isBlank: false),
                          ratio: seed.firstSnapPath.flatMap { aspects.ratio(for: $0) },
                          width: width, u: CardLayout.u(cardWidth: width), soulNames: [:], soulGlyphs: soulGlyphs,
                          soulOrder: request.soulOrder, accessible: true)
        }
    }

    // MARK: - The print (D6)

    /// A completion waits for the Complete flow's cover to go, so the print is
    /// seen; then it starts once.
    private var printReady: Bool {
        model.completion != nil && !isPresentingComplete && printStart == nil
    }

    private func beginPrint(_ proxy: ScrollViewProxy) async {
        let start = PrintStart.decide(place: placeFrame, viewport: viewport, reduceMotion: reduceMotion)
        if start == .scrollThenPrint {
            withAnimation(.easeInOut(duration: 0.3)) { proxy.scrollTo(BareMomentPage.placeID, anchor: .bottom) }
            try? await Task.sleep(for: .milliseconds(350))
            // The page left while it scrolled: nothing started, nothing to clear.
            guard !Task.isCancelled else { return }
        }
        turn = CardTurnModel(face: .verso)
        printCrossfades = start == .crossfade
        host.startPrinting(request.momentId)
        printStart = .now
    }

    /// The last frame: the card at rest, verso up; announced once.
    private func endPrint() {
        printStart = nil
        host.endPrinting()
        model.printEnded()
    }

    /// D7: a relaunch mid-print lands the card at rest, verso up.
    private func cutShortIfRestored() {
        guard let detail = model.detail,
              PrintStart.cutsShort(printingFor: host.printingFor, momentId: request.momentId,
                                   hasPebble: detail.pebble != nil) else { return }
        turn = CardTurnModel(face: .verso)
        model.cutShort()
        host.endPrinting()
    }

    // MARK: - Data the card reads

    /// Shares the pebble, whose id differs from the moment's; public pebbles only.
    private var shareURL: URL? {
        guard let pebble = model.detail?.pebble, pebble.visibility == .public else { return nil }
        return SharedPebbleLink.url(for: pebble.id)
    }

    private var menuItems: [PebblesMenuItem] {
        HeldCardMenu.items(hasPebble: model.detail?.pebble != nil,
                           editMoment: { isPresentingEditMoment = true },
                           editPebble: { isPresentingEditPebble = true },
                           delete: {
                               if let detail = model.detail { pendingDeletion = MomentDeleteTarget(detail: detail) }
                           })
    }

    private var collectionGlyphs: [UUID: Glyph] {
        Dictionary(refs.collections.compactMap { collection in collection.glyph.map { (collection.id, $0) } },
                   uniquingKeysWith: { first, _ in first })
    }

    private var soulGlyphs: [String: [GlyphStroke]] {
        Dictionary(refs.souls.map { ($0.id.uuidString.lowercased(), $0.glyph.strokes) },
                   uniquingKeysWith: { first, _ in first })
    }

    // MARK: - Changes

    private func reload() async {
        await model.load { try await service.moment(id: request.momentId) }
    }

    private func saved() {
        Task { await reload() }
        model.changed(revision)
    }

    private func delete(_ option: MomentDeleteOption, on target: MomentDeleteTarget) async {
        let outcome = await model.delete(option, target: target, revision: revision) { option, target in
            try await MomentDeletion.perform(option, on: target, client: supabase.client, drafts: completeDrafts)
        }
        switch outcome {
        case .closes:
            // Before the close, so it fades: the slot has gone from the Path.
            host.markLeaving()
            onClose()
        case .reloads:
            // In place: the page keeps showing until the fresh detail lands.
            await reload()
        case .failed:
            deleteError = String(localized: "Something went wrong. Please try again.")
        case .ignored:
            break
        }
    }
}

/// The phone width the page assumes until its first layout reports its own.
private enum ReferencePhone {
    static let width: CGFloat = 393
}

/// The FLIP on whichever card the page shows (#1160 D1): the transform from the
/// host's state, the rest read outside it, and the card invisible until its
/// rest is known, so the first frame of an open is measured, not guessed.
private struct Flying: ViewModifier {
    let host: HeldCardHostModel
    let onRest: (CGRect) -> Void
    var active = true

    func body(content: Content) -> some View {
        content
            .modifier(FlightEffect(progress: active ? host.flight : 1, slot: active ? host.sourceRect : nil,
                                   rest: host.restRect))
            .opacity(host.phase == .opening && host.flies && host.restRect == nil ? 0 : 1)
            .background {
                Color.clear
                    .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { onRest($0) }
            }
    }
}
