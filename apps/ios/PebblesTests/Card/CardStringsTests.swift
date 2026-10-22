import Foundation
import Testing
@testable import Pebbles

/// #1146 §Strings: every new key in both languages; French says galet.
@Suite("Card strings")
struct CardStringsTests {
    private func resolved(_ resource: LocalizedStringResource, _ language: String) -> String {
        var copy = resource
        copy.locale = Locale(identifier: language)
        return String(localized: copy)
    }

    // swiftlint:disable:next large_tuple
    static let rows: [(LocalizedStringResource, String, String)] = [
        (CardStrings.turn, "Turn", "Retourner"),
        (CardStrings.turnToPebble, "Pebble", "Galet"),
        (CardStrings.turnToMoment, "Moment", "Moment"),
        (CardStrings.turnActionToPebble, "Turn over to your pebble", "Retourner vers ton galet"),
        (CardStrings.turnActionToMoment, "Turn over to the moment", "Retourner vers le moment"),
        (CardStrings.versoHeading, "Your pebble", "Ton galet"),
        (CardStrings.domainsA11y, "Life domains", "Domaines de vie"),
        (CardStrings.collectionsA11y, "Collections", "Collections"),
        (CardStrings.placeKicker, "Not a pebble yet", "Pas encore un galet"),
        (CardStrings.placeBody,
         "Its card prints here once you say what it meant: a feeling and its weight, "
             + "the parts of your life it touched, who was there.",
         "Sa carte s'imprimera ici quand tu diras ce qu'il a compté : un sentiment et son poids, "
             + "les parts de ta vie qu'il a touchées, qui était là.")
    ]

    @Test("every key resolves in English and French")
    func bothLanguages() {
        for (resource, english, french) in Self.rows {
            #expect(resolved(resource, "en") == english)
            #expect(resolved(resource, "fr") == french)
        }
    }

    @Test("French says galet, never caillou")
    func galet() {
        for (resource, _, _) in Self.rows {
            #expect(!resolved(resource, "fr").localizedCaseInsensitiveContains("caillou"))
        }
    }
}
