#if DEBUG
import CoreImage
import Foundation
import os

/// Builds `filter-lab-<yyyy-MM-dd>.zip` (spec "The export"): the inputs,
/// `looks.json`, `measures.json`, and each look as this phone renders it.
/// It renders every look: call it off the main actor.
enum FilterLabExport {
    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "filter-lab-export")

    static func makeZip(document: LabDocument, measures: LabMeasuresDocument, root: URL,
                        date: Date = .now) throws -> URL {
        let fm = FileManager.default
        let staging = fm.temporaryDirectory
            .appending(path: UUID().uuidString)
            .appending(path: "filter-lab-\(dayString(date))")
        try fm.createDirectory(at: staging.appending(path: "photos"), withIntermediateDirectories: true)
        let encoder = FilterLabStore.makeEncoder()
        try encoder.encode(document).write(to: staging.appending(path: "looks.json"))
        try encoder.encode(measures).write(to: staging.appending(path: "measures.json"))

        for photo in document.photos {
            let source = root.appending(path: photo.file)
            try fm.copyItem(at: source, to: staging.appending(path: photo.file))
            let faceCount = measures.measures[photo.id.uuidString]?.faceCount ?? 0
            try renderLooks(of: photo, source: source, faceCount: faceCount,
                            into: staging.appending(path: "renders/\(photo.id.uuidString)"))
        }
        return try zip(staging)
    }

    private static func renderLooks(of photo: LabPhoto, source: URL, faceCount: Int, into directory: URL) throws {
        guard !photo.looks.isEmpty else { return }
        guard let image = FilterLabStore.loadImage(at: source),
              let space = image.colorSpace ?? CGColorSpace(name: CGColorSpace.sRGB) else {
            logger.error("export could not load \(photo.file, privacy: .public)")
            throw FilterLabError.renderFailed(photo.file)
        }
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        // Vision only when a look needs it.
        let needsMasks = photo.looks.contains { $0.params.refocus != 0 || $0.params.autoEnhance }
        let masks = needsMasks ? FilterMasks.compute(for: image, faceCount: faceCount) : .none
        let input = CIImage(cgImage: image)
        for look in photo.looks {
            let output = FilterPipeline.apply(look.params, to: input, masks: masks)
            guard let rendered = FilterPipeline.render(output, context: FilterPipeline.sharedContext,
                                                       colorSpace: space) else {
                logger.error("export render failed for \(photo.file, privacy: .public)")
                throw FilterLabError.renderFailed("\(photo.file) / \(look.name)")
            }
            try FilterLabStore.writeJPEG(rendered, to: directory.appending(path: "\(look.id.uuidString).jpg"))
        }
    }

    /// A coordinated read with `.forUploading` hands back a zip of a directory,
    /// with no dependency. The system deletes its copy on return, so it is copied out.
    static func zip(_ directory: URL) throws -> URL {
        let destination = directory.deletingLastPathComponent()
            .appending(path: directory.lastPathComponent + ".zip")
        var result: Result<URL, Error> = .failure(FilterLabError.zipFailed)
        var coordinationError: NSError?
        NSFileCoordinator().coordinate(readingItemAt: directory, options: .forUploading,
                                       error: &coordinationError) { zipped in
            do {
                try FileManager.default.copyItem(at: zipped, to: destination)
                result = .success(destination)
            } catch {
                logger.error("zip copy failed: \(error.localizedDescription, privacy: .public)")
                result = .failure(error)
            }
        }
        if let coordinationError {
            logger.error("zip coordination failed: \(coordinationError.localizedDescription, privacy: .public)")
            throw coordinationError
        }
        return try result.get()
    }

    static func dayString(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "UTC")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: date)
    }
}
#endif
