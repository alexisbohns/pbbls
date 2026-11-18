#if DEBUG
import CoreImage
import ImageIO
import PhotosUI
import SwiftUI
import UIKit
import os

/// The filter lab (#1675), Settings → Developer: import through the crop editor,
/// tune looks per photo, export the session for the preset analysis.
struct FilterLabView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var store = FilterLabStore()
    @State private var pickedItem: PhotosPickerItem?
    @State private var cropping: PendingCrop?
    @State private var exportFile: ExportFile?
    @State private var isWorking = false
    @State private var status: String?
    @State private var confirmsReset = false
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "filter-lab")

    struct PendingCrop: Identifiable {
        let id = UUID()
        let source: CropSource
        let hasDepth: Bool
    }

    struct ExportFile: Identifiable {
        let id = UUID()
        let url: URL
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                if let status {
                    Text(verbatim: status).padding(.horizontal, 16)
                }
                if store.document.photos.isEmpty {
                    Text(verbatim: "Add photos with +. Each one opens the crop editor first.")
                        .padding(16)
                }
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 104), spacing: 8)], spacing: 8) {
                    ForEach(store.document.photos) { photo in
                        NavigationLink(value: photo.id) {
                            FilterLabThumbnail(url: store.root.appending(path: photo.file), looks: photo.looks.count)
                        }
                        .buttonStyle(.plain)
                        .contextMenu {
                            Button { deletePhoto(photo) } label: { Text(verbatim: "Remove photo") }
                        }
                    }
                }
                .padding(16)
                .allowsHitTesting(!isWorking)
            }
            .navigationTitle(Text(verbatim: "Filter lab"))
            .navigationDestination(for: UUID.self) { destination($0) }
            .toolbar { toolbar }
            .overlay { if isWorking { ProgressView() } }
        }
        .onChange(of: pickedItem) { _, item in
            guard let item else { return }
            pickedItem = nil
            Task { await beginImport(item) }
        }
        .fullScreenCover(item: $cropping) { pending in
            PhotoCropEditor(source: pending.source,
                            onCancel: { cropping = nil },
                            onDone: { image, _ in
                                cropping = nil
                                Task { await finishImport(image, hasDepth: pending.hasDepth) }
                            })
        }
        .sheet(item: $exportFile) { FilterLabShareSheet(url: $0.url) }
        .confirmationDialog(Text(verbatim: "Delete every photo and look in the lab?"),
                            isPresented: $confirmsReset, titleVisibility: .visible) {
            Button { resetSession() } label: { Text(verbatim: "Start over") }
        }
    }

    @ToolbarContentBuilder
    private var toolbar: some ToolbarContent {
        ToolbarItem(placement: .cancellationAction) {
            Button { dismiss() } label: { Text(verbatim: "Close") }
        }
        ToolbarItemGroup(placement: .primaryAction) {
            PhotosPicker(selection: $pickedItem, matching: .images) {
                Image(systemName: "plus").accessibilityLabel(Text(verbatim: "Add a photo"))
            }
            Menu {
                Button { Task { await export() } } label: { Text(verbatim: "Export session") }
                    .disabled(store.document.photos.isEmpty)
                Button { confirmsReset = true } label: { Text(verbatim: "Start over…") }
            } label: {
                Image(systemName: "ellipsis.circle").accessibilityLabel(Text(verbatim: "More"))
            }
        }
    }

    private func destination(_ id: UUID) -> some View {
        FilterLookEditor(photoID: id, measures: store.measures.measures[id.uuidString], store: store)
    }

    private func beginImport(_ item: PhotosPickerItem) async {
        do {
            guard let data = try await item.loadTransferable(type: Data.self) else {
                logger.warning("lab import: the picked photo carried no data")
                status = "That photo carried no data."
                return
            }
            guard let source = await CropSource.make(from: data) else {
                status = "That photo's format is not supported."
                return
            }
            status = nil
            cropping = PendingCrop(source: source, hasDepth: PhotoMeasures.hasDepth(data))
        } catch {
            logger.error("lab import failed: \(error.localizedDescription, privacy: .public)")
            status = "Could not load that photo."
        }
    }

    private func finishImport(_ image: CGImage, hasDepth: Bool) async {
        isWorking = true
        defer { isWorking = false }
        let measures = await Task.detached(priority: .userInitiated) {
            PhotoMeasures.measure(image, faceCount: PhotoMeasures.faceCount(in: image), hasDepth: hasDepth,
                                  autoEnhance: FilterMasks.autoEnhanceFilters(for: image))
        }.value
        do {
            _ = try store.add(image, measures: measures)
        } catch {
            logger.error("lab photo save failed: \(error.localizedDescription, privacy: .public)")
            status = "Could not save that photo."
        }
    }

    private func export() async {
        isWorking = true
        defer { isWorking = false }
        let document = store.document, measures = store.measures, root = store.root
        do {
            let url = try await Task.detached(priority: .userInitiated) {
                try FilterLabExport.makeZip(document: document, measures: measures, root: root)
            }.value
            exportFile = ExportFile(url: url)
        } catch {
            logger.error("lab export failed: \(error.localizedDescription, privacy: .public)")
            status = "Export failed."
        }
    }

    private func deletePhoto(_ photo: LabPhoto) {
        do {
            try store.deletePhoto(photo.id)
        } catch {
            logger.error("lab photo delete failed: \(error.localizedDescription, privacy: .public)")
            status = "Could not remove that photo."
        }
    }

    private func resetSession() {
        do {
            try store.reset()
        } catch {
            logger.error("lab reset failed: \(error.localizedDescription, privacy: .public)")
            status = "Could not start over."
        }
    }
}

/// A grid cell: a small decode of the photo and its look count.
private struct FilterLabThumbnail: View {
    let url: URL
    let looks: Int
    @State private var image: CGImage?

    var body: some View {
        Color.clear
            .aspectRatio(1, contentMode: .fit)
            .overlay {
                if let image {
                    Image(decorative: image, scale: 1).resizable().scaledToFill()
                } else {
                    ProgressView()
                }
            }
            .clipped()
            .overlay(alignment: .bottomTrailing) {
                Text(verbatim: "\(looks)")
                    .font(.caption.monospacedDigit())
                    .padding(4)
                    .background(.thinMaterial)
                    .padding(4)
            }
            .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: looks == 1 ? "Photo, 1 look" : "Photo, \(looks) looks"))
        .task(id: url) {
            let url = self.url
            image = await Task.detached(priority: .utility) { () -> CGImage? in
                guard let source = CGImageSourceCreateWithURL(url as CFURL, nil) else { return nil }
                let options = [kCGImageSourceCreateThumbnailFromImageAlways: true,
                               kCGImageSourceThumbnailMaxPixelSize: 312] as CFDictionary
                return CGImageSourceCreateThumbnailAtIndex(source, 0, options)
            }.value
        }
    }
}

/// The system share sheet for the export zip (AirDrop to the Mac).
private struct FilterLabShareSheet: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: [url], applicationActivities: nil)
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}
#endif
