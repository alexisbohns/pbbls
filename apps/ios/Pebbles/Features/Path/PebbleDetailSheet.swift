import SwiftUI
import os

/// Read-view sheet for a single moment. Loads the `MomentDetail` from
/// `v_moments_full` and renders `PebbleReadView` with a privacy badge + Edit button
/// in the navigation bar. Tapping Edit stacks `EditPebbleSheet` on top; on
/// save the read view reloads in place and notifies the parent.
///
/// Used as the destination for both:
/// - Path-list tap of an existing pebble.
/// - Post-create reveal after `CreatePebbleSheet` completes.
struct PebbleDetailSheet: View {
    let momentId: UUID
    var onPebbleUpdated: (() -> Void)?

    @Environment(SupabaseService.self) private var supabase
    @Environment(EmotionPaletteService.self) private var palettes
    @Environment(\.dismiss) private var dismiss

    @State private var detail: MomentDetail?
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var isPresentingEdit = false

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
                            PebbleToolbarButton("Edit") {
                                isPresentingEdit = true
                            }
                            .disabled(detail?.pebble == nil)
                        }
                    }
                }
                .pebblesScreen()
                .toolbarBackground(.hidden, for: .navigationBar)
        }
        .task { await load() }
        .sheet(isPresented: $isPresentingEdit) {
            EditPebbleSheet(momentId: momentId, onSaved: {
                Task { await load() }
                onPebbleUpdated?()
            })
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
            PebbleReadView(detail: detail, palette: detail.pebble.flatMap { palettes.palette(for: $0.emotion.id) })
        }
    }

    private func load() async {
        isLoading = true
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
}
