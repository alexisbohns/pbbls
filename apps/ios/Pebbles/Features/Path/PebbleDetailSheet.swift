import SwiftUI
import os

/// Read-view sheet for a single moment. Loads the `MomentDetail` from
/// `v_moments_full` and renders `PebbleReadView` with a privacy badge and a
/// `…` menu in the navigation bar (R7). **Edit pebble** stacks
/// `EditPebbleSheet` on top; on save the read view reloads in place and
/// notifies the parent. **Delete** opens the shared confirmation (R8): deleting
/// the moment closes the page, deleting only the pebble reloads it as a bare
/// moment.
///
/// Used as the destination for both:
/// - Path-list tap of an existing pebble.
/// - Post-create reveal after `CreatePebbleSheet` completes.
struct PebbleDetailSheet: View {
    let momentId: UUID
    var onPebbleUpdated: (() -> Void)?

    @Environment(SupabaseService.self) private var supabase
    @Environment(EmotionPaletteService.self) private var palettes
    @Environment(CompleteDraftStore.self) private var completeDrafts
    @Environment(\.dismiss) private var dismiss

    @State private var detail: MomentDetail?
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var isPresentingEdit = false
    @State private var isPresentingComplete = false
    @State private var pendingDeletion: MomentDeleteTarget?
    @State private var deleteError: String?
    @State private var isDeleting = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "pebble-detail")

    var body: some View {
        NavigationStack {
            content
                .pebblesToolbarTitle(MomentName.display(detail?.name) ?? "")
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) {
                        if let pebble = detail?.pebble {
                            PebblePrivacyBadge(visibility: pebble.visibility, style: .chip)
                        }
                    }
                    ToolbarItem(placement: .topBarTrailing) {
                        HStack(spacing: 8) {
                            // Shares the PEBBLE, whose id differs from the moment's.
                            if let pebble = detail?.pebble, pebble.visibility == .public {
                                ShareLink(item: SharedPebbleLink.url(for: pebble.id)) {
                                    Image(systemName: "square.and.arrow.up")
                                }
                                .accessibilityLabel(Text(
                                    "Share",
                                    comment: "Accessibility label for the public pebble share button"
                                ))
                            }
                            Menu {
                                if detail?.pebble != nil {
                                    Button { isPresentingEdit = true } label: {
                                        Label("Edit pebble", systemImage: "pencil")
                                    }
                                }
                                Button(role: .destructive) {
                                    if let detail { pendingDeletion = MomentDeleteTarget(detail: detail) }
                                } label: {
                                    Label("Delete", systemImage: "trash")
                                }
                            } label: {
                                Image(systemName: "ellipsis.circle")
                            }
                            .disabled(detail == nil || isDeleting || isLoading)
                            .accessibilityLabel(Text("More"))
                        }
                    }
                }
                .pebblesScreen()
                .toolbarBackground(.hidden, for: .navigationBar)
        }
        .interactiveDismissDisabled(isDeleting)
        .task { await load(showsSpinner: detail == nil) }
        .sheet(isPresented: $isPresentingEdit) {
            EditPebbleSheet(momentId: momentId, onSaved: {
                Task { await load(showsSpinner: false) }
                onPebbleUpdated?()
            })
        }
        .fullScreenCover(isPresented: $isPresentingComplete) {
            CompleteFlowView(
                momentId: momentId,
                momentName: detail?.name,
                onCompleted: { _ in
                    // In place, behind the success screen.
                    Task { await load(showsSpinner: false) }
                    onPebbleUpdated?()
                }
            )
        }
        .momentDeleteDialog(target: $pendingDeletion) { target, option in
            Task { await delete(option, on: target) }
        }
        .alert(
            "Couldn't delete",
            isPresented: Binding(
                get: { deleteError != nil },
                set: { if !$0 { deleteError = nil } }
            ),
            presenting: deleteError
        ) { _ in
            Button("OK", role: .cancel) { deleteError = nil }
        } message: { message in
            Text(message)
        }
    }

    private func delete(_ option: MomentDeleteOption, on target: MomentDeleteTarget) async {
        guard !isDeleting else { return }
        isDeleting = true
        defer { isDeleting = false }
        do {
            try await MomentDeletion.perform(
                option, on: target, client: supabase.client, drafts: completeDrafts
            )
            onPebbleUpdated?()
            if target.closesReadPage(after: option) {
                dismiss()
            } else {
                // In place: the page keeps showing until the fresh detail
                // lands, and the menu stays disabled until then.
                await load(showsSpinner: false)
            }
        } catch {
            logger.error("delete failed: \(error.localizedDescription, privacy: .private)")
            deleteError = String(localized: "Something went wrong. Please try again.")
        }
    }

    @ViewBuilder
    private var content: some View {
        if isLoading {
            ProgressView()
        } else if let loadError {
            VStack(spacing: 12) {
                Text(loadError).foregroundStyle(.secondary)
                Button("Retry") { Task { await load() } }
            }
        } else if let detail {
            // Look up the pebble's emotion palette so the whole read page tints
            // to it (#605). A cache miss (nil) leaves the page on system chrome.
            PebbleReadView(
                detail: detail,
                palette: detail.pebble.flatMap { palettes.palette(for: $0.emotion.id) },
                onComplete: { isPresentingComplete = true }
            )
        }
    }

    /// `showsSpinner: false` refreshes `detail` in place after an edit or a
    /// delete, so the page is not swapped for a spinner and back.
    private func load(showsSpinner: Bool = true) async {
        if showsSpinner { isLoading = true }
        loadError = nil
        do {
            self.detail = try await MomentsService(client: supabase.client).moment(id: momentId)
            self.isLoading = false
        } catch {
            logger.error("pebble detail load failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Couldn't load this pebble."
            self.isLoading = false
        }
    }
}

#Preview {
    let supabase = SupabaseService()
    return PebbleDetailSheet(momentId: UUID())
        .environment(supabase)
        .environment(ReferenceDataService(client: supabase.client))
        .environment(EmotionPaletteService(client: supabase.client))
        .environment(SnapURLCache(client: supabase.client))
        .environment(CompleteDraftStore())
}
