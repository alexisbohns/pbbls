import Foundation
import ImageIO

/// Reads the capture timestamp out of picked image bytes (D7).
///
/// Must run *before* `ImagePipeline.process`, which deliberately produces
/// metadata-free JPEGs — by the time bytes reach Storage the capture date is
/// gone, which is right for what we upload and useless for what we want to ask.
///
/// Read from the picked bytes rather than `PHAsset.creationDate`: the latter
/// needs a photo-library authorization the app does not request, to learn
/// something already present in data we have loaded anyway.
///
/// Pure: no I/O, no logging, no global state.
enum ExifCaptureDate {

    /// EXIF `DateTimeOriginal` is `yyyy:MM:dd HH:mm:ss` with no zone — a wall
    /// clock in whatever timezone the camera was in. We interpret it as local
    /// time, which is right for the overwhelmingly common case of recording a
    /// moment from a photo taken nearby.
    ///
    /// `en_US_POSIX` is mandatory here and is *not* the locale-pinning the iOS
    /// guidelines forbid: this parses a fixed machine format, not a
    /// user-facing date. A device locale with a non-Gregorian calendar would
    /// otherwise misread it.
    private static let formatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy:MM:dd HH:mm:ss"
        formatter.timeZone = .current
        return formatter
    }()

    /// The photo's capture date, or nil when the data is not an image, carries
    /// no EXIF date, or carries one we cannot parse. Every nil path means the
    /// caller falls back to now — never to a date we guessed.
    static func from(_ data: Data) -> Date? {
        guard !data.isEmpty,
              let source = CGImageSourceCreateWithData(data as CFData, nil),
              CGImageSourceGetCount(source) > 0,
              let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil)
                  as? [CFString: Any],
              let exif = properties[kCGImagePropertyExifDictionary] as? [CFString: Any],
              let raw = exif[kCGImagePropertyExifDateTimeOriginal] as? String
        else {
            return nil
        }
        return formatter.date(from: raw)
    }
}
