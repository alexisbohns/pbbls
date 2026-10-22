import SwiftUI
import os

/// The held card's page (#1146 D2): its own top bar over the card in a
/// vertical `ScrollView`. One view serves the interim sheet (E1–E3) and the
/// overlay (E4). It loads the moment in `.task`, reloads it in place after an
/// edit, a pebble delete or a completion, and presents the edit sheets, the
/// Complete flow and the delete dialog over the window as before. A moment with
/// no pebble still shows the old read view until E3's bare page.
struct HeldCardPage: View {
    let request: HeldCardRequest
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

    init(request: HeldCardRequest, onClose: @escaping () -> Void) {
        self.request = request
        self.onClose = onClose
        _model = State(initialValue: HeldCardModel(momentId: request.momentId, origin: request.origin,
                                                   onChanged: request.onChanged))
        _turn = State(initialValue: CardTurnModel(face: request.face))
    }

    private var service: MomentsService { MomentsService(client: supabase.client) }

    var body: some View {
        VStack(spacing: 0) {
            CardTopBar(shareURL: shareURL, menuItems: menuItems,
                       isDisabled: model.detail == nil || model.isDeleting, onBack: onClose)
            content
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .background(Color.pebbles.surface.ignoresSafeArea())
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { pageWidth = $0 }
        .task { await reload() }
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
            ProgressView()
        case .failed:
            VStack(spacing: 12) {
                Text("Couldn't load this pebble.")
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                Button("Retry") { Task { await reload() } }
            }
        case .loaded:
            if let detail = model.detail {
                if let pebble = detail.pebble {
                    card(detail, pebble)
                } else {
                    // Until E3's bare page.
                    PebbleReadView(detail: detail, palette: nil, onComplete: { isPresentingComplete = true })
                }
            }
        }
    }

    private func card(_ detail: MomentDetail, _ pebble: MomentDetail.Pebble) -> some View {
        ScrollView {
            PebbleCard(detail: detail, pebble: pebble, palette: palettes.palette(for: pebble.emotion.id),
                       ratio: detail.snaps.first.flatMap { aspects.ratio(for: $0.storagePath) },
                       width: CardLayout.cardWidth(available: pageWidth), domains: refs.domains,
                       collectionGlyphs: collectionGlyphs, soulGlyphs: soulGlyphs, soulOrder: request.soulOrder,
                       turn: turn)
                .frame(maxWidth: .infinity)
                .padding(.top, 8)
                .padding(.bottom, 32)
        }
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
