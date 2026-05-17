import CoreGraphics

/// Six-step spacing scale rooted on the 17pt iOS body baseline.
/// `lg` equals the body font size (17), `xxl` equals `lg * 2` (34).
/// Use these constants in place of literal pt values for paddings,
/// stack spacings, and corner radii so visual rhythm stays consistent
/// across screens.
enum Spacing {
    static let xs:  CGFloat = 3
    static let sm:  CGFloat = 10
    static let md:  CGFloat = 13
    static let lg:  CGFloat = 17    // root, == iOS body font size
    static let xl:  CGFloat = 22
    static let xxl: CGFloat = 34    // == lg * 2
}
