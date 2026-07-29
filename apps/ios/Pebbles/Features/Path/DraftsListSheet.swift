import SwiftUI
import os

/// Unpublished quick captures, most recently saved first (M47).
///
/// Its own surface rather than a section inside the Path timeline, whose week
/// grouping, ripple, bounce and stats all assume real pebbles (design D4).
/// Tapping a row resumes it in `CreatePebbleSheet`.
struct DraftsListSheet: View {
    /// Called after a resumed draft publishes, so the Path can reload and reveal
    /// the new pebble — same contract as `CreatePebbleSheet.onCreated`.
    let onPebbleCreated: (UUID) -> Void

    @Environment(PebbleDraftsService.self) private var draftsService
    @Environment(\.dismiss) private var dismiss

    @State private var drafts: [PebbleDraftRecord] = []
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var resuming: PebbleDraftRecord?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "drafts-list")

    var body: some View {
        NavigationStack {
            content
                .pebblesToolbarTitle("Drafts")
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        PebbleToolbarButton("Done") { dismiss() }
                    }
                }
                .pebblesScreen()
        }
        .task { await load() }
        .sheet(item: $resuming) { draft in
            CreatePebbleSheet(
                onCreated: { pebbleId in
                    resuming = nil
                    onPebbleCreated(pebbleId)
                    dismiss()
                },
                resuming: draft
            )
        }
        .onChange(of: resuming) { _, newValue in
            // The resume sheet may have saved the draft again or published it;
            // either way the list is stale once it closes.
            if newValue == nil { Task { await load() } }
        }
    }

    @ViewBuilder
    private var content: some View {
        if isLoading {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let loadError {
            VStack(spacing: 12) {
                Text(loadError)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                Button("Try again") { Task { await load() } }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .padding()
        } else if drafts.isEmpty {
            Text("No drafts yet. Anything you start keeping shows up here.")
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .padding()
        } else {
            List {
                ForEach(drafts) { draft in
                    Button {
                        resuming = draft
                    } label: {
                        DraftRow(draft: draft)
                    }
                    .buttonStyle(.plain)
                }
                .onDelete(perform: delete)
            }
        }
    }

    private func load() async {
        isLoading = true
        loadError = nil
        do {
            drafts = try await draftsService.list()
            isLoading = false
        } catch {
            logger.error("drafts load failed: \(error.localizedDescription, privacy: .private)")
            loadError = "Couldn't load your drafts. Please try again."
            isLoading = false
        }
    }

    private func delete(at offsets: IndexSet) {
        let doomed = offsets.map { drafts[$0] }
        drafts.remove(atOffsets: offsets)
        Task {
            for draft in doomed {
                do {
                    try await draftsService.delete(id: draft.id)
                } catch {
                    logger.error("draft delete failed: \(error.localizedDescription, privacy: .private)")
                    await load()
                    return
                }
            }
            await draftsService.refreshCount()
        }
    }
}

/// A draft has no `render_svg` — nothing is carved until it publishes — so the
/// row leads with a placeholder rather than a pebble visual.
private struct DraftRow: View {
    let draft: PebbleDraftRecord

    var body: some View {
        HStack(spacing: 12) {
            RoundedRectangle(cornerRadius: 8)
                .strokeBorder(style: StrokeStyle(lineWidth: 1, dash: [3, 3]))
                .foregroundStyle(.secondary)
                .frame(width: 36, height: 36)
                .overlay {
                    Image(systemName: "square.dashed")
                        .foregroundStyle(.secondary)
                        .font(.footnote)
                }

            VStack(alignment: .leading, spacing: 2) {
                if let name = draft.payload.name, !name.isEmpty {
                    Text(name)
                        .lineLimit(1)
                } else {
                    Text("No name yet")
                        .italic()
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
                Text(draft.updatedAt, format: .relative(presentation: .named))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
        }
    }
}
