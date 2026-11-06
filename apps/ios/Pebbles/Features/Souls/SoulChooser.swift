import SwiftUI
import os

/// The one chooser, three hosts (souls spec Summary): a header slot (the
/// People tab's prompts), the Usual tiles, then everyone A to Z or the search
/// results, and the bottom bar. In `.pick` the tray carries the pills and the
/// button; in `.browse` tiles and rows open a person.
///
/// The host passes `model` (its selection is the host's answer), the souls to
/// render come from `ReferenceDataService` and are refreshed on appear because
/// the cache only refreshes on soul mutations, so counts would lag every pebble
/// recorded since launch.
struct SoulChooser<Header: View>: View {
    let model: SoulChooserModel
    let title: LocalizedStringResource?
    /// Browse mode: a tile or row was tapped.
    var onOpen: (SoulWithGlyph) -> Void = { _ in }
    /// Pick mode: the tray's button.
    var onConfirm: () -> Void = {}
    /// A soul was created inline; the host decides what follows (a picker
    /// selects it, the People tab opens it).
    var onCreated: (SoulWithGlyph) -> Void = { _ in }
    /// Browse mode, when set: a long press on a tile or a row offers Delete
    /// and the host confirms (the People tab owns the dialog and the delete).
    var onDeleteRequest: ((SoulWithGlyph) -> Void)?
    /// Reference renders only: `ImageRenderer` draws neither ScrollView content nor TextFields.
    var scrolls = true
    @ViewBuilder var header: () -> Header

    @Environment(SupabaseService.self) private var supabase
    @Environment(ReferenceDataService.self) private var refs
    @Environment(AchievementsService.self) private var achievements

    @State private var addError: String?
    @State private var isAdding = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "souls.chooser")

    var body: some View {
        Group {
            if scrolls {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 0) { list }
                        .padding(.bottom, Spacing.xxl)
                }
                .scrollDismissesKeyboard(.interactively)
                .safeAreaInset(edge: .bottom, spacing: 0) { bottomBar }
            } else {
                VStack(spacing: 0) {
                    // A flexible frame grows to a taller child; the clear canvas keeps the remaining height.
                    Color.clear
                        .overlay(alignment: .top) {
                            VStack(alignment: .leading, spacing: 0) { list }
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .clipped()
                    bottomBar
                }
            }
        }
        .task {
            await refs.refreshSouls()
            // A failed refetch with an empty cache must not wipe a host-seeded selection.
            if !refs.souls.isEmpty { model.replaceSouls(refs.souls) }
        }
        .onChange(of: refs.souls) { _, souls in model.replaceSouls(souls) }
        .onChange(of: model.isSearching) { _, searching in
            if !searching { addError = nil }
        }
    }

    // MARK: Sections

    @ViewBuilder
    private var list: some View {
        if let title {
            Text(title)
                .pebblesFont(.title)
                .foregroundStyle(Color.pebbles.onSurface)
                .padding(.horizontal, Spacing.lg)
                .padding(.bottom, Spacing.md)
        }
        header()
        if model.isSearching && !model.trimmedQuery.isEmpty {
            results
        } else {
            usual
            sections
        }
    }

    private var bottomBar: some View {
        VStack(spacing: Spacing.sm) {
            if let label = model.buttonLabel {
                SoulPillTray(
                    selected: model.selection.compactMap { id in model.souls.first { $0.id == id } },
                    label: label,
                    onRemove: { model.remove($0) },
                    onConfirm: onConfirm,
                    scrolls: scrolls
                )
                .padding(.horizontal, Spacing.lg)
            }
            SoulChooserBottomBar(model: model, onAdd: { name in Task { await add(name) } }, addError: addError,
                                 rendersStatic: !scrolls)
                .disabled(isAdding)
        }
        .padding(.vertical, Spacing.sm)
        .background(
            LinearGradient(colors: [Color.pebbles.surface.opacity(0), Color.pebbles.surface],
                           startPoint: .top, endPoint: .bottom)
                .frame(height: 24),
            alignment: .top
        )
        .background(Color.pebbles.surface.opacity(0.92).ignoresSafeArea(edges: .bottom))
    }

    @ViewBuilder
    private var usual: some View {
        let people = model.usual
        if !people.isEmpty {
            sectionTitle(Text("Usual"))
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 4), count: 4), spacing: 4) {
                ForEach(people) { soul in
                    deletable(soul) {
                        SoulTile(soul: soul, isSelected: model.isSelected(soul.id),
                                 showsSelection: model.mode != .browse) {
                            tapped(soul)
                        }
                    }
                }
            }
            .padding(.horizontal, Spacing.md)
            .padding(.bottom, Spacing.sm)
        }
    }

    @ViewBuilder
    private var sections: some View {
        ForEach(model.sections) { section in
            sectionTitle(Text(verbatim: section.letter))
            VStack(spacing: 0) {
                ForEach(section.souls) { soul in
                    row(soul)
                }
            }
            .padding(.horizontal, Spacing.lg)
        }
    }

    @ViewBuilder
    private var results: some View {
        VStack(spacing: 0) {
            if model.canAdd {
                SoulAddRow(name: model.trimmedQuery) { Task { await add(model.trimmedQuery) } }
                    .padding(.horizontal, Spacing.lg)
                    .padding(.bottom, Spacing.sm)
            }
            ForEach(model.results) { soul in
                row(soul)
            }
            .padding(.horizontal, Spacing.lg)
        }
        usual
    }

    private func row(_ soul: SoulWithGlyph) -> some View {
        deletable(soul) {
            SoulRow(soul: soul, trailing: trailing(for: soul)) { tapped(soul) }
                .overlay(alignment: .bottom) { Divider().overlay(Color.pebbles.rule) }
        }
    }

    /// The host's Delete on a long press, only when it asked for one.
    @ViewBuilder
    private func deletable<Content: View>(_ soul: SoulWithGlyph, @ViewBuilder content: () -> Content) -> some View {
        if let onDeleteRequest {
            content()
                .pebblesContextMenu([.delete { onDeleteRequest(soul) }])
        } else {
            content()
        }
    }

    private func trailing(for soul: SoulWithGlyph) -> SoulRow.Trailing {
        if model.mode == .browse { return .chevron }
        return model.isSelected(soul.id) ? .tick : .plus
    }

    private func sectionTitle(_ text: Text) -> some View {
        text
            .pebblesFont(.cardHeading)
            .foregroundStyle(Color.pebbles.onSurfaceVariant)
            .padding(.horizontal, Spacing.lg)
            .padding(.top, Spacing.md)
            .padding(.bottom, Spacing.xs)
    }

    // MARK: Actions

    private func tapped(_ soul: SoulWithGlyph) {
        switch model.mode {
        case .browse:
            onOpen(soul)
        case .pick:
            TapHaptics.play(.selection)
            model.toggle(soul.id)
        }
    }

    private func add(_ name: String) async {
        guard !isAdding else { return }
        guard let userId = supabase.session?.user.id else {
            logger.error("inline soul create without a session")
            return
        }
        isAdding = true
        addError = nil
        do {
            let created = try await SoulInlineCreate.create(name: name, userId: userId, client: supabase.client)
            achievements.fireCheck()
            model.didCreate(created)
            onCreated(created)
            await refs.refreshSouls()
        } catch {
            logger.error("inline soul create failed: \(error.localizedDescription, privacy: .private)")
            addError = String(localized: "Couldn't save the soul. Please try again.")
        }
        isAdding = false
    }
}

extension SoulChooser where Header == EmptyView {
    init(model: SoulChooserModel, title: LocalizedStringResource?,
         onOpen: @escaping (SoulWithGlyph) -> Void = { _ in },
         onConfirm: @escaping () -> Void = {},
         onCreated: @escaping (SoulWithGlyph) -> Void = { _ in },
         onDeleteRequest: ((SoulWithGlyph) -> Void)? = nil,
         scrolls: Bool = true) {
        self.init(model: model, title: title, onOpen: onOpen, onConfirm: onConfirm, onCreated: onCreated,
                  onDeleteRequest: onDeleteRequest, scrolls: scrolls) {
            EmptyView()
        }
    }
}
