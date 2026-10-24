import Foundation
import Testing
@testable import Pebbles

/// The week's sentences (#1226), Android's `WrapCopyTest` and
/// `WrapCopyFrenchTest` week cases word for word: the parity proof that the two
/// apps read the same.
@Suite("Wrap copy · week")
struct WrapCopyWeekTests {
    private let en = Vectors.en
    private let fr = Vectors.fr
    private let calendar = Vectors.paris

    @Test("the line counts in words, says so far for the live week, and reads an empty week")
    func lineEnglish() {
        #expect(WrapCopy.line(pebbles: 11, people: 5, live: false, hasMoments: true, locale: en)
                == "Eleven pebbles, five people.")
        #expect(WrapCopy.line(pebbles: 13, people: 14, live: true, hasMoments: true, locale: en)
                == "Thirteen pebbles, fourteen people so far.")
        #expect(WrapCopy.line(pebbles: 1, people: 0, live: false, hasMoments: true, locale: en) == "One pebble.")
        #expect(WrapCopy.line(pebbles: 0, people: 0, live: true, hasMoments: true, locale: en) == "No pebble yet.")
        #expect(WrapCopy.line(pebbles: 0, people: 0, live: true, hasMoments: false, locale: en) == "Nothing yet.")
        #expect(WrapCopy.week(41, locale: en) == "week 41")
    }

    @Test("the leads arrange names the server ranked")
    func leadsEnglish() {
        #expect(WrapCopy.emotionsLead(["Joy", "Peace", "Pride"], locale: en) == "Mostly joy, then peace.")
        #expect(WrapCopy.emotionsLead(["Anger"], locale: en) == "Mostly anger.")
        #expect(WrapCopy.domainsLead(["Friends", "Identity", "Family", "Travel"], locale: en)
                == "Friends first, then identity and family.")
        #expect(WrapCopy.weightLead(bright: 6, even: 4, heavy: 3, locale: en) == "Six bright, four even, three heavy.")
        // The short time puts a narrow no-break space before the day period, as the JDK's does.
        #expect(WrapCopy.hoursLead(from: 8.04, to: 22.5, calendar: calendar, locale: en, dateLocale: en)
                == "From 8:02\u{202F}AM to 10:30\u{202F}PM.")
    }

    @Test("the invitation names the week's own moments")
    func invitationEnglish() {
        #expect(WrapCopy.invitation(["Flag march", "Rent", "Couldn’t sleep"], long: false, locale: en)
                == "Was someone there for Flag march, Rent or Couldn’t sleep?")
        #expect(WrapCopy.invitation(["Flag march"], long: true, locale: en)
                == "Was someone there for Flag march? Name a soul once; it joins every moment you link.")
    }

    @Test("kept counts photos and cards")
    func keptEnglish() {
        #expect(WrapCopy.photos(6, locale: en) == "six photos")
        #expect(WrapCopy.cards(1, locale: en) == "one card written")
        #expect(WrapCopy.cardsShort(2, locale: en) == "2 cards")
    }

    @Test("a tile says its list, and what it does")
    func tileEnglish() {
        #expect(WrapCopy.tile(.contents, locale: en) == "contents")
        #expect(WrapCopy.tileLabel(.contents, body: "thirteen", locale: en) == "contents: thirteen.")
        #expect(WrapCopy.tileHint(locale: en) == "Opens the list.")
        #expect(WrapCopy.size(.medium, locale: en) == "medium")
        #expect(WrapCopy.polarity(.lowlight, locale: en) == "heavy")
    }

    @Test("the line agrees in gender and number")
    func lineFrench() {
        #expect(WrapCopy.line(pebbles: 11, people: 5, live: false, hasMoments: true, locale: fr)
                == "Onze galets, cinq personnes.")
        #expect(WrapCopy.line(pebbles: 1, people: 1, live: true, hasMoments: true, locale: fr)
                == "Un galet, une personne pour l'instant.")
        #expect(WrapCopy.line(pebbles: 0, people: 0, live: true, hasMoments: false, locale: fr)
                == "Rien pour l'instant.")
    }

    @Test("the leads and counts read in French")
    func leadsFrench() {
        #expect(WrapCopy.emotionsLead(["Joie", "Paix"], locale: fr) == "Surtout joie, puis paix.")
        #expect(WrapCopy.domainsLead(["Amis", "Identité", "Famille"], locale: fr)
                == "Amis d'abord, puis identité et famille.")
        #expect(WrapCopy.weightLead(bright: 6, even: 4, heavy: 3, locale: fr)
                == "Six clairs, quatre égaux, trois lourds.")
        #expect(WrapCopy.hoursLead(from: 8.04, to: 22.5, calendar: calendar, locale: fr, dateLocale: fr)
                == "De 08:02 à 22:30.")
        #expect(WrapCopy.photos(1, locale: fr) == "une photo")
        #expect(WrapCopy.cards(2, locale: fr) == "deux cartes écrites")
        #expect(WrapCopy.tileLabel(.kept, body: "trois", locale: fr) == "gardé : trois.")
    }
}
