import Foundation

/// Each task's copy (spec §1.4): the row's title and line, the sheet's body
/// and its one call to action. Literal keys, so the catalog sees each one.
extension HomeChecklist.Task {
    var title: LocalizedStringResource {
        switch self {
        case .moment: "home.task.moment.title"
        case .pebble: "home.task.pebble.title"
        case .collection: "home.task.collection.title"
        case .soul: "home.task.soul.title"
        case .glyph: "home.task.glyph.title"
        }
    }

    var line: LocalizedStringResource {
        switch self {
        case .moment: "home.task.moment.line"
        case .pebble: "home.task.pebble.line"
        case .collection: "home.task.collection.line"
        case .soul: "home.task.soul.line"
        case .glyph: "home.task.glyph.line"
        }
    }

    var body: LocalizedStringResource {
        switch self {
        case .moment: "home.task.moment.body"
        case .pebble: "home.task.pebble.body"
        case .collection: "home.task.collection.body"
        case .soul: "home.task.soul.body"
        case .glyph: "home.task.glyph.body"
        }
    }

    var cta: LocalizedStringResource {
        switch self {
        case .moment: "home.task.moment.cta"
        case .pebble: "home.task.pebble.cta"
        case .collection: "home.task.collection.cta"
        case .soul: "home.task.soul.cta"
        case .glyph: "home.task.glyph.cta"
        }
    }

    /// The illustration's short VoiceOver label.
    var illustrationLabel: LocalizedStringResource {
        switch self {
        case .moment: "home.task.moment.illustration"
        case .pebble: "home.task.pebble.illustration"
        case .collection: "home.task.collection.illustration"
        case .soul: "home.task.soul.illustration"
        case .glyph: "home.task.glyph.illustration"
        }
    }
}
