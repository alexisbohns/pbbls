import SwiftUI
import os

/// Edit-mode sheet for profile management. Presented from `ProfileView`'s gear button.
///
/// Sections shown depend on whether the account is SSO (Apple/Google) or email-only:
/// SSO sees a read-only Providers list; email-only sees a Password change form.
struct SettingsSheet: View {
    let initialDisplayName: String
    let initialGlyphId: UUID?
    let initialGlyphStrokes: [GlyphStroke]?
    let email: String?
    let onSaved: (_ displayName: String, _ glyph: Glyph?) -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var displayName: String
    @State private var pickedGlyph: Glyph?
    @State private var currentPassword: String = ""
    @State private var newPassword: String = ""
    @State private var isSaving = false
    @State private var saveError: String?
    @State private var presentedLegalDoc: LegalDoc?
    @State private var isPresentingGlyphPicker = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "settings-sheet")

    init(
        initialDisplayName: String,
        initialGlyphId: UUID?,
        initialGlyphStrokes: [GlyphStroke]?,
        email: String?,
        onSaved: @escaping (_ displayName: String, _ glyph: Glyph?) -> Void
    ) {
        self.initialDisplayName = initialDisplayName
        self.initialGlyphId = initialGlyphId
        self.initialGlyphStrokes = initialGlyphStrokes
        self.email = email
        self.onSaved = onSaved
        self._displayName = State(initialValue: initialDisplayName)
    }

    private var trimmedDisplayName: String {
        displayName.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var isDirty: Bool {
        let nameChanged = trimmedDisplayName != initialDisplayName && !trimmedDisplayName.isEmpty
        let glyphChanged = pickedGlyph != nil && pickedGlyph?.id != initialGlyphId
        let passwordSet = !newPassword.isEmpty
        return nameChanged || glyphChanged || passwordSet
    }

    private var currentStrokes: [GlyphStroke]? {
        pickedGlyph?.strokes ?? initialGlyphStrokes
    }

    var body: some View {
        NavigationStack {
            Form {
                headerSection
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if isSaving {
                        ProgressView()
                    } else {
                        Button("Save") { Task { await save() } }
                            .disabled(!isDirty)
                    }
                }
            }
            .pebblesScreen()
            .sheet(isPresented: $isPresentingGlyphPicker) {
                GlyphPickerSheet(currentGlyphId: pickedGlyph?.id ?? initialGlyphId) { glyph in
                    pickedGlyph = glyph
                }
            }
            .sheet(item: $presentedLegalDoc) { doc in
                LegalDocumentSheet(url: doc.url)
                    .ignoresSafeArea()
            }
        }
    }

    private var headerSection: some View {
        Section {
            Button {
                isPresentingGlyphPicker = true
            } label: {
                HStack {
                    Spacer()
                    glyphView
                    Spacer()
                }
                .padding(.vertical, 8)
            }
            .buttonStyle(.plain)
            .listRowBackground(Color.clear)
        }
    }

    @ViewBuilder
    private var glyphView: some View {
        if let strokes = currentStrokes, !strokes.isEmpty {
            GlyphThumbnail(strokes: strokes, side: 120)
        } else {
            RoundedRectangle(cornerRadius: 8)
                .fill(Color.secondary.opacity(0.08))
                .frame(width: 120, height: 120)
                .overlay {
                    Image(systemName: "scribble")
                        .font(.title)
                        .foregroundStyle(Color.pebblesMutedForeground)
                }
        }
    }

    private func save() async {
        // Implemented in Task 6.
    }
}

#Preview("Email user") {
    SettingsSheet(
        initialDisplayName: "Alexis",
        initialGlyphId: nil,
        initialGlyphStrokes: nil,
        email: "hello@bohns.design",
        onSaved: { _, _ in }
    )
    .environment(SupabaseService())
}
