#if DEBUG
import CoreImage
import Foundation
import os

/// Builds `filter-lab-<yyyy-MM-dd>.zip` (spec "The export"): the inputs,
/// `looks.json`, `measures.json`, and each look as this phone renders it.
/// The zip's single root entry is `filter-lab-<date>/`.
/// It renders every look: call it off the main actor.
enum FilterLabExport {
    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "filter-lab-export")

    /// The one folder exports live in: wiped at the start of each, so at most one is on disk.
    static var workDirectory: URL {
        FileManager.default.temporaryDirectory.appending(path: "FilterLabExport", directoryHint: .isDirectory)
    }

    static func makeZip(document: LabDocument, measures: LabMeasuresDocument, root: URL,
                        date: Date = .now) throws -> URL {
        let staging = try stage(document: document, measures: measures, root: root, date: date)
        defer { removeStaging(staging) }
        return try zip(staging)
    }

    /// Lays the export out as a folder, `workDirectory/filter-lab-<date>`.
    static func stage(document: LabDocument, measures: LabMeasuresDocument, root: URL,
                      date: Date = .now) throws -> URL {
        let fm = FileManager.default
        let staging = workDirectory.appending(path: "filter-lab-\(dayString(date))", directoryHint: .isDirectory)
        do {
            if fm.fileExists(atPath: workDirectory.path(percentEncoded: false)) {
                try fm.removeItem(at: workDirectory)
            }
            try fm.createDirectory(at: staging.appending(path: "photos"), withIntermediateDirectories: true)
            let encoder = FilterLabStore.makeEncoder()
            try encoder.encode(document).write(to: staging.appending(path: "looks.json"))
            try encoder.encode(measures).write(to: staging.appending(path: "measures.json"))
        } catch {
            logger.error("export could not start: \(error.localizedDescription, privacy: .public)")
            throw error
        }

        for photo in document.photos {
            let source = root.appending(path: photo.file)
            do {
                try fm.copyItem(at: source, to: staging.appending(path: photo.file))
            } catch {
                let reason = error.localizedDescription
                logger.error("export could not copy \(photo.file, privacy: .public): \(reason, privacy: .public)")
                throw error
            }
            let faceCount = measures.measures[photo.id.uuidString]?.faceCount ?? 0
            try renderLooks(of: photo, source: source, faceCount: faceCount,
                            into: staging.appending(path: "renders/\(photo.id.uuidString)"))
        }
        return staging
    }

    private static func removeStaging(_ staging: URL) {
        do {
            try FileManager.default.removeItem(at: staging)
        } catch {
            logger.error("could not remove the export staging folder: \(error.localizedDescription, privacy: .public)")
        }
    }

    private static func renderLooks(of photo: LabPhoto, source: URL, faceCount: Int, into directory: URL) throws {
        guard !photo.looks.isEmpty else { return }
        guard let image = FilterLabStore.loadImage(at: source) else {
            logger.error("export could not load \(photo.file, privacy: .public)")
            throw FilterLabError.renderFailed(photo.file)
        }
        // A grey photo's own space would render a grey JPEG; keep it RGB.
        guard let space = (image.colorSpace?.model == .rgb ? image.colorSpace : nil)
                ?? CGColorSpace(name: CGColorSpace.sRGB) else {
            logger.error("export has no colour space for \(photo.file, privacy: .public)")
            throw FilterLabError.renderFailed(photo.file)
        }
        do {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        } catch {
            logger.error("export could not create renders: \(error.localizedDescription, privacy: .public)")
            throw error
        }
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
