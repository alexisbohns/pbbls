import Foundation

/// The Collections note's words (spec §3, E2), one accessor per key so the
/// views name no key twice. All keys live in `Localizable.xcstrings`.
enum CollectionsNoteCopy {
    static var kicker: String { String(localized: "collections.note.kicker") }
    static var sentence: String { String(localized: "collections.note.sentence") }
    static var step1: String { String(localized: "collections.note.step1") }
    static var step2: String { String(localized: "collections.note.step2") }
    static var step2Stack: String { String(localized: "collections.note.step2.stack") }
    static var step2Pack: String { String(localized: "collections.note.step2.pack") }
    static var step2Track: String { String(localized: "collections.note.step2.track") }
    static var step3: String { String(localized: "collections.note.step3") }
    static var aside: String { String(localized: "collections.note.aside") }
    static var start: String { String(localized: "collections.note.start") }
    static var sample: String { String(localized: "collections.note.sample") }
    static var footnote: String { String(localized: "collections.footnote") }
    static var footnoteDismiss: String { String(localized: "collections.footnote.dismiss") }
}
