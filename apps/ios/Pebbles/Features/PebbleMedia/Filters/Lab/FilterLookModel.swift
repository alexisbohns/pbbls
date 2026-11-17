#if DEBUG
import CoreImage
import Foundation
import Observation
import os

/// The tuning screen's state. Slider changes coalesce: at most one render runs,
/// and one more follows if the values moved meanwhile.
@MainActor
@Observable
final class FilterLookModel {
    let photo: LabPhoto
    let source: CGImage
    let faceCount: Int
    let aestheticsBefore: Double?

    var params = FilterParams.neutral {
        didSet { if params != oldValue { scheduleRender() } }
    }
    var showsOriginal = false
    var lookName = ""
    private(set) var editingLookID: UUID?
    private(set) var preview: CGImage?
    private(set) var masks = FilterMasks.none
    private(set) var masksReady = false
    private(set) var lastAesthetics: Double?
    private(set) var isSaving = false
    private(set) var errorMessage: String?

    private var rendering = false
    private var dirty = false
    private let colorSpace: CGColorSpace?
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "filter-lab-editor")

    init(photo: LabPhoto, source: CGImage, faceCount: Int, aestheticsBefore: Double?) {
        self.photo = photo
        self.source = source
        self.faceCount = faceCount
        self.aestheticsBefore = aestheticsBefore
        colorSpace = source.colorSpace ?? CGColorSpace(name: CGColorSpace.sRGB)
        preview = source
    }

    var subjectFound: Bool { masks.subject != nil }

    func loadMasks() async {
        guard !masksReady else { return }
        let source = self.source, faces = faceCount
        masks = await Task.detached(priority: .userInitiated) {
            FilterMasks.compute(for: source, faceCount: faces)
        }.value
        masksReady = true
        if params.refocus != 0 || params.autoEnhance { scheduleRender() }
    }

    func load(_ look: LabLook) {
        params = look.params
        lookName = look.name
        editingLookID = look.id
        lastAesthetics = look.aesthetics
    }

    func duplicate(_ look: LabLook) {
        load(look)
        lookName = look.name + " copy"
        editingLookID = nil
    }

    func startNewLook() {
        params = .neutral
        lookName = ""
        editingLookID = nil
        lastAesthetics = nil
    }

    /// Saves as a new look, or updates the one being edited. Scores the render first.
    func save(into store: FilterLabStore, asNew: Bool) async {
        guard !isSaving else { return }
        isSaving = true
        defer { isSaving = false }
        let score = await renderOffMain().flatMap { PhotoMeasures.aesthetics(of: $0) }
        let existing = asNew ? nil : store.looks(of: photo.id).first { $0.id == editingLookID }
        let trimmed = lookName.trimmingCharacters(in: .whitespaces)
        let name = trimmed.isEmpty ? "Look \(store.looks(of: photo.id).count + 1)" : trimmed
        let look = LabLook(id: existing?.id ?? UUID(), name: name,
                           createdAt: existing?.createdAt ?? LabLook.timestamp(), params: params, aesthetics: score)
        do {
            try store.save(look, in: photo.id)
            editingLookID = look.id
            lookName = name
            lastAesthetics = score
            errorMessage = nil
        } catch {
            logger.error("look save failed: \(error.localizedDescription, privacy: .public)")
            errorMessage = "Could not save the look."
        }
    }

    func delete(_ look: LabLook, from store: FilterLabStore) {
        do {
            try store.deleteLook(look.id, in: photo.id)
            if editingLookID == look.id { editingLookID = nil }
        } catch {
            logger.error("look delete failed: \(error.localizedDescription, privacy: .public)")
            errorMessage = "Could not delete the look."
        }
    }

    private func scheduleRender() {
        guard !rendering else { dirty = true; return }
        rendering = true
        Task { await renderLoop() }
    }

    private func renderLoop() async {
        repeat {
            dirty = false
            if let image = await renderOffMain() {
                preview = image
            } else {
                logger.error("preview render failed")
            }
        } while dirty
        rendering = false
    }

    private func renderOffMain() async -> CGImage? {
        guard let space = colorSpace else { return nil }
        let params = self.params, masks = self.masks, source = self.source
        return await Task.detached(priority: .userInitiated) {
            FilterPipeline.render(FilterPipeline.apply(params, to: CIImage(cgImage: source), masks: masks),
                                  context: FilterPipeline.sharedContext, colorSpace: space)
        }.value
    }
}
#endif
