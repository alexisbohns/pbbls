import SwiftUI
import UIKit

extension Font {
    /// Ysabeau-SemiBold with OpenType proportional + lining figures
    /// (numbers align to cap height, proportional widths). Used everywhere
    /// Ysabeau renders mixed text + numbers so digits look right.
    ///
    /// Feature constants from `CoreText/SFNTLayoutTypes.h`:
    ///   - Number Spacing (type 6) → Proportional Numbers (selector 1)
    ///   - Number Case  (type 21) → Upper Case Numbers / lining (selector 1)
    static func ysabeauSemibold(_ size: CGFloat) -> Font {
        let descriptor = UIFontDescriptor(name: "Ysabeau-SemiBold", size: size)
            .addingAttributes([
                .featureSettings: [
                    [UIFontDescriptor.FeatureKey.type: 6,  UIFontDescriptor.FeatureKey.selector: 1],
                    [UIFontDescriptor.FeatureKey.type: 21, UIFontDescriptor.FeatureKey.selector: 1],
                ],
            ])
        return Font(UIFont(descriptor: descriptor, size: size))
    }
}
