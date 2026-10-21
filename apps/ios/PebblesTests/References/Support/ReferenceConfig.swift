import SwiftUI
import UIKit

/// Paper is the light scheme, Night the dark one.
enum ReferenceScheme: String, CaseIterable, Sendable {
    case paper, night

    var colorScheme: ColorScheme { self == .paper ? .light : .dark }
    var userInterfaceStyle: UIUserInterfaceStyle { self == .paper ? .light : .dark }
}

/// The locale pass this process runs. `String(localized:)` ignores the SwiftUI
/// environment, so French is a whole second run (`xcodebuild -testLanguage fr`),
/// and a reference declares which passes render it.
enum ReferenceLocale: String, CaseIterable, Sendable {
    case en, fr

    static var current: ReferenceLocale {
        Bundle.main.preferredLocalizations.first?.hasPrefix("fr") == true ? .fr : .en
    }
}

/// Every pin a reference carries (spec §B2 D3). Width, scale, scheme, locale,
/// time zone and Dynamic Type are set here; the device and runtime are pinned
/// in `ios.yml`.
struct ReferenceConfig {
    /// The citable reference width: an iPhone 15, the maintainer's device.
    static let phoneWidth: CGFloat = 393

    var scheme: ReferenceScheme
    var locales: Set<ReferenceLocale> = [.en]
    /// `.large`, or `.accessibility2` for an `-ax2` variant.
    var dynamicType: DynamicTypeSize = .large
    /// nil renders a component at its own fixed frame.
    var width: CGFloat? = ReferenceConfig.phoneWidth

    func runs(in locale: ReferenceLocale) -> Bool {
        locales.contains(locale)
    }

    /// `<name>-<paper|night>-<en|fr>[-ax2].png`
    func fileName(_ name: String, locale: ReferenceLocale) -> String {
        precondition(dynamicType == .large || dynamicType == .accessibility2,
                     "references render at .large or .accessibility2 only")
        let suffix = dynamicType == .accessibility2 ? "-ax2" : ""
        return "\(name)-\(scheme.rawValue)-\(locale.rawValue)\(suffix).png"
    }
}
