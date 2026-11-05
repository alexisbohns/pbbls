import SwiftUI
import os

/// Make it richer (record-flow D12, Riverbed treatment): the lit stone with
/// its derived glyph, "+N karma", the name, the week with the new stone on
/// its day, then three rows that each edit the finished pebble: collection
/// and visibility through `complete_pebble` (keyed on moment and caller, so
/// a second call is an edit), words through `save_moment` (the edit sheet).
/// Done returns to the origin; Record another starts a fresh capture.
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

    @State private var collectionId: UUID?
    @State private var visibility: Visibility
    @State private var hasWords: Bool
    @State private var words: MomentDetail?
    @State private var editError: String?
    /// One edit at a time: the rows sit out while a write or the words fetch is in flight.
    @State private var isEditing = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "complete-richer")

    init(momentId: UUID, name: String?, happenedAt: Date?, result: CompletionResult, draft: CompleteDraft,
         hasWords: Bool, onExit: @escaping () -> Void, onRecordAnother: (() -> Void)?, onEdited: @escaping () -> Void,
         scrolls: Bool = true) {
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
    }

    var body: some View {
        Group {
            if scrolls {
                ScrollView { content }
            } else {
                content
            }
        }
        .sheet(item: $words) { detail in
            EditMomentSheet(detail: detail, onSaved: {
                hasWords = true
                onEdited()
            })
        }
    }

    private var content: some View {
        VStack(spacing: Spacing.xl) {
            PebbleStoneView(
                valence: draft.valence ?? .neutralMedium,
                glyph: result.glyph,
                palette: draft.emotionId.flatMap { palettes.palette(for: $0) },
                followsTilt: true
            )
            .frame(maxWidth: .infinity)
            .frame(height: 240)
            .padding(.top, Spacing.lg)

            VStack(spacing: Spacing.sm) {
                MomentNameText(name: name, untitledFont: .ysabeauSemiboldItalic(28))
                    .pebblesFont(.title)
                    .multilineTextAlignment(.center)
                if let karma = result.karmaDelta, karma > 0 {
                    HStack(spacing: 6) {
                        PbblsIconImage(.karmaDot, size: 18, relativeTo: .headline,
                                       style: AnyShapeStyle(Color.pebbles.primary))
                        Text("+\(karma) karma").pebblesFont(.headline).foregroundStyle(Color.pebbles.onSurface)
                    }
                    .accessibilityElement(children: .combine)
                    .accessibilityLabel(Text("Earned \(karma) karma"))
                }
                if let happenedAt {
                    RicherWeekRow(date: happenedAt)
                        .padding(.top, Spacing.sm)
                }
            }

            VStack(alignment: .leading, spacing: Spacing.sm) {
                Text("MAKE IT RICHER")
                    .pebblesFont(.meta)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                Group {
                    if !refs.collections.isEmpty { collectionRow }
                    visibilityRow
                    if !hasWords { wordsRow }
                }
                .disabled(isEditing)
                if let editError {
                    Text(verbatim: editError).pebblesFont(.subhead).foregroundStyle(Color.pebbles.error)
                }
            }

            VStack(spacing: Spacing.xs) {
                Button("Done", action: onExit).buttonStyle(PebblesPrimaryButtonStyle())
                if let onRecordAnother {
                    Button("Record another", action: onRecordAnother)
                        .pebblesFont(.bodyEmphasized)
                        .foregroundStyle(Color.pebbles.primary)
                        .frame(maxWidth: .infinity, minHeight: 44)
                }
            }
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.bottom, Spacing.lg)
    }

    // MARK: Rows

    /// The icon set's tick, as a plain `Image` so a `Menu` item can carry it.
    private var tick: some View {
        Image(PbblsIcon.checkmarkDot.assetName).renderingMode(.template)
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

    private func row(icon: PbblsIcon, title: LocalizedStringResource, value: String?) -> some View {
        HStack(spacing: Spacing.md) {
            PbblsIconImage(icon, size: 24, style: AnyShapeStyle(Color.pebbles.onSurfaceVariant))
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
struct RicherWeekRow: View {
    struct Day: Identifiable {
        let date: Date
        let isMarked: Bool
        var id: Date { date }
    }

    let date: Date

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

    var body: some View {
        HStack(spacing: Spacing.sm) {
            ForEach(Self.days(around: date, calendar: SpineCalendar.current)) { day in
                VStack(spacing: Spacing.xs) {
                    Text(verbatim: day.date.formatted(.dateTime.weekday(.narrow).locale(locale)))
                        .pebblesFont(.meta)
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    Circle()
                        .fill(day.isMarked ? Color.pebbles.primary : Color.pebbles.outlineVariant)
                        .frame(width: day.isMarked ? 10 : 6, height: day.isMarked ? 10 : 6)
                        .frame(height: 10)
                }
                .frame(maxWidth: .infinity)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: date.formatted(.dateTime.weekday(.wide).day().month().locale(locale))))
    }
}
