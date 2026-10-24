import Foundation
import Testing
@testable import Pebbles

/// The insights' words (#1226). Android: `WrapCopyTest`'s and
/// `WrapCopyFrenchTest`'s insights assertions, verbatim.
@Suite("WrapCopy insights")
struct WrapCopyInsightsTests {
    // swiftlint:disable:next type_name
    typealias V = Vectors

    @Test("the insights read the server's verdict and window")
    func english() {
        #expect(WrapCopy.toneLine(.brightMoreThanTwoToOne, first: .highlight, last: .neutral, locale: V.en)
            == "Bright outweighed heavy more than two to one; it opened bright and closed even.")
        #expect(WrapCopy.toneLine(.allEven, first: nil, last: nil, locale: V.en) == "Everything even this month.")
        #expect(WrapCopy.toneLine(.unknown, first: .lowlight, last: .highlight, locale: V.en)
            == "It opened heavy and closed bright.")
        #expect(WrapCopy.hoursWindow(20, calendar: V.paris, locale: V.en, dateLocale: V.en)
            == "8:00\u{202F}PM – midnight")
        #expect(WrapCopy.hoursOf(24, locale: V.en) == "of 24 collected")
    }

    @Test("the insights read in French")
    func french() {
        #expect(WrapCopy.toneLine(.brightTwoToOne, first: .lowlight, last: .highlight, locale: V.fr)
            == "Le clair l'emporte sur le lourd à deux contre un; il s'est ouvert lourd et refermé clair.")
        #expect(WrapCopy.hoursWindow(20, calendar: V.paris, locale: V.fr, dateLocale: V.fr) == "20:00 – minuit")
        #expect(WrapCopy.hoursOf(1, locale: V.fr) == "sur 1 ramassé")
    }

    @Test("the dial said in words, by the pebbles collected")
    func description() {
        #expect(WrapCopy.hoursDescription(count: 9, total: 24, start: 20, calendar: V.paris, locale: V.en,
                                          dateLocale: V.en)
            == "9 of 24 pebbles collected between 8:00\u{202F}PM and midnight.")
        #expect(WrapCopy.hoursDescription(count: 1, total: 1, start: 8, calendar: V.paris, locale: V.fr,
                                          dateLocale: V.fr)
            == "1 galet sur 1 ramassé entre 08:00 et 12:00.")
    }
}
