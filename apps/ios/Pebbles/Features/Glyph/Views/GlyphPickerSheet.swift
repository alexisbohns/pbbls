import SwiftUI
import os

/// Sheet that lists the user's glyphs and offers to carve a new one.
/// Presented from `PebbleFormView`'s "Glyph" row.
struct GlyphPickerSheet: View {
    let currentGlyphId: UUID?
    let onSelected: (UUID?) -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var glyphs: [Glyph] = []
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var showCarveSheet = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "glyph-picker")
    private var service: GlyphService { GlyphService(supabase: supabase) }

    private let columns = [GridItem(.adaptive(minimum: 96), spacing: 12)]

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("Choose a glyph")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Close") { dismiss() }
                    }
                }
                .pebblesScreen()
                .task { await load() }
                .fullScreenCover(isPresented: $showCarveSheet) {
                    GlyphCarveSheet(onSaved: { glyph in
                        glyphs.insert(glyph, at: 0)
                        onSelected(glyph.id)
                        dismiss()
                    })
                }
        }
    }

    @ViewBuilder
    private var content: some View {
        if isLoading {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let loadError {
            ContentUnavailableView(
                "Couldn't load glyphs",
                systemImage: "exclamationmark.triangle",
                description: Text(loadError)
            )
            .overlay(alignment: .bottom) {
                Button("Retry") { Task { await load() } }
                    .padding()
            }
        } else {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    carveNewRow

                    if !glyphs.isEmpty {
                        Text("Your glyphs")
                            .font(.headline)
                            .padding(.top)

                        LazyVGrid(columns: columns, spacing: 12) {
                            ForEach(glyphs) { glyph in
                                Button {
                                    onSelected(glyph.id)
                                    dismiss()
                                } label: {
                                    GlyphThumbnail(
                                        strokes: glyph.strokes,
                                        side: 96,
                                        backgroundColor: glyph.id == currentGlyphId
                                            ? Color.accentColor.opacity(0.15)
                                            : Color.secondary.opacity(0.08)
                                    )
                                }
                                .accessibilityLabel(glyph.name ?? "Untitled glyph")
                            }
                        }
                    }
                }
                .padding()
            }
        }
    }

    private var carveNewRow: some View {
        Button {
            showCarveSheet = true
        } label: {
            HStack(spacing: 12) {
                RoundedRectangle(cornerRadius: 8)
                    .strokeBorder(style: StrokeStyle(lineWidth: 1, dash: [4]))
                    .frame(width: 48, height: 48)
                    .overlay(Image(systemName: "plus"))
                Text("Carve new glyph")
                    .font(.body)
                Spacer()
                Image(systemName: "chevron.right")
                    .foregroundStyle(.secondary)
            }
            .padding(12)
            .background(Color.secondary.opacity(0.06))
            .clipShape(RoundedRectangle(cornerRadius: 10))
        }
        .buttonStyle(.plain)
    }

    private func load() async {
        isLoading = true
        loadError = nil
        do {
            self.glyphs = try await service.list()
        } catch {
            logger.error("glyphs list failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Please try again."
        }
        self.isLoading = false
    }
}

#Preview {
    GlyphPickerSheet(currentGlyphId: nil, onSelected: { _ in })
        .environment(SupabaseService())
}
