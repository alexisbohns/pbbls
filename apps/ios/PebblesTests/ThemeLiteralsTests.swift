import Foundation
import Testing

/// D8 (spec 2026-10-20-ios-m3-colour §7), the iOS twin of Android's
/// ThemeLiteralsTest. Feature code names a colour only as
/// `Color.pebbles.<role>`. The simulator shares the host filesystem, so the test
/// reads the sources from `#filePath` (precedent: StoneLab/StoneLabPalettesTests).
///
/// A file's count is its number of offending *lines* (one line can break two
/// rules: `UIColor(red:` also matches `Color(red`). The ratchet fails when a
/// count rises ("new literal") and when one falls without its `debt` entry
/// being lowered ("lower the debt").
@Suite("Theme literals")
struct ThemeLiteralsTests {

    struct Rule {
        let regex: NSRegularExpression
        let reason: String

        // NSRegularExpression, not a Swift Regex literal: the first rule needs
        // a lookbehind.
        init(_ pattern: String, _ reason: String) {
            guard let regex = try? NSRegularExpression(pattern: pattern) else {
                preconditionFailure("invalid pattern \(pattern)")
            }
            self.regex = regex
            self.reason = reason
        }
    }

    /// SwiftUI's system and absolute colours, as one regex alternation.
    static let absoluteColours = "red|orange|yellow|green|mint|teal|cyan|blue|indigo|purple|pink|brown|gray|white|black"

    static let rules: [Rule] = [
        Rule(#"(?<![\w.])(Color)?\.("# + absoluteColours + #")\b"#,
             "a system or absolute colour: use a Color.pebbles role"),
        Rule(#"Color\.(primary|secondary|accentColor)\b"#,
             "a system label colour: use onSurface / onSurfaceVariant / primary"),
        Rule(#"Color\(\s*(red|white|hue|\.sRGB|\.displayP3)"#, "a literal colour"),
        Rule(#"Color\(hex:\s*""#, "a hex literal (Color(hex: variable) on server data is fine)"),
        Rule(#"Color\(""#, "a named asset: only the generated PebblesColors.swift names assets"),
        Rule(#"UIColor\((red|white|hue|named|displayP3Red):"#, "a UIKit literal: use UIColor.pebbles(_:)"),
        Rule(#"UIColor\.(system\w+|label|secondaryLabel|tertiaryLabel|white|black|red|clear)\b"#,
             "a UIKit system colour: use UIColor.pebbles(_:)"),
        Rule(#"role:\s*\.destructive"#, "a destructive role outside a system .alert / .confirmationDialog")
    ]

    static let scannedFolders = ["Features", "Components"]

    /// Not scanned (spec §7).
    static let exemptPrefixes = [
        "Features/Path/Stone/",
        "Features/StoneLab/",
        "Features/Path/Models/EmotionPalette.swift",
        "Components/Buttons/AppleSignInButton.swift",
        "Components/Buttons/GoogleSignInButton.swift"
    ]

    /// Permanent: hits that are not chrome colours.
    static let allowlist: [String: (count: Int, reason: String)] = [
        "Features/Path/Valence/ValenceStoneStyle.swift": (1, "joySurface, Joy's emotion surface (data)"),
        "Features/Path/Read/PebbleSnapFrame.swift": (1, "preview fixture image drawn above #Preview"),
        "Features/Path/Components/WeekPathView.swift": (2, "fade mask stops (alpha only)"),
        "Features/Path/Render/PebbleAnimatedRenderView.swift": (1, "stroke-reveal mask"),
        "Features/Welcome/HandcraftedLogoView.swift": (1, "stroke-reveal mask"),
        "Features/Connections/InviteSheet.swift": (1, "QR quiet zone must be white for scanners"),
        "Features/Capture/CaptureFlowView.swift": (1, "role: .destructive in a system dialog"),
        "Features/Path/MomentDeleteDialog.swift": (1, "role: .destructive in a system dialog"),
        "Features/Profile/Sheets/SettingsSheet.swift": (1, "role: .destructive in a system dialog"),
        "Features/Profile/Lists/SoulsListView.swift": (1, "role: .destructive in a system dialog"),
        "Features/Connections/ConnectionsListView.swift": (2, "role: .destructive in a system dialog"),
        "Features/Profile/Lists/CollectionsListView.swift": (1, "role: .destructive in a system dialog"),
        "Features/Glyph/Views/GlyphCarveSheet.swift": (1, "role: .destructive in a system dialog")
    ]

    /// Shrinks to nothing by the end of the stack (A2: .red and owned
    /// destructive roles; A3a / A3b: the folders they migrate). Never raise it.
    static let debt: [String: Int] = [
        "Components/Buttons/PebblesPrimaryButtonStyle.swift": 2,
        "Components/Checkboxes/PebblesCheckbox.swift": 1,
        "Components/Inputs/PebblesTextInput.swift": 1,
        "Components/PebbleRow.swift": 1,
        "Features/Auth/AuthView.swift": 2,
        "Features/Capture/CaptureFlowView.swift": 1,
        "Features/Complete/Steps/CompletePrivacyStep.swift": 1,
        "Features/Connections/ConnectionsListView.swift": 1,
        "Features/Connections/InviteAcceptSheet.swift": 1,
        "Features/Connections/InviteSheet.swift": 1,
        "Features/Glyph/Views/GlyphCanvasView.swift": 1,
        "Features/Glyph/Views/GlyphCarveSheet.swift": 2,
        "Features/Glyph/Views/GlyphDetailDrawer.swift": 2,
        "Features/Glyph/Views/GlyphPickerContent.swift": 1,
        "Features/Glyph/Views/GlyphTabBar.swift": 2,
        "Features/Glyph/Views/GlyphsListView.swift": 1,
        "Features/Glyph/Views/SlideToConfirm.swift": 1,
        "Features/Karma/AchievementMomentView.swift": 1,
        "Features/Karma/KarmaEarnedCapsule.swift": 2,
        "Features/Lab/Components/FeaturedCommunityCard.swift": 1,
        "Features/Onboarding/OnboardingView.swift": 3,
        "Features/Path/Components/PathPebbleRow.swift": 3,
        "Features/Path/Components/WeekPathView.swift": 1,
        "Features/Path/EditMomentSheet.swift": 1,
        "Features/Path/PebbleDetailSheet.swift": 1,
        "Features/Path/PebbleFormView.swift": 1,
        "Features/Path/Valence/ValenceStoneStyle.swift": 1,
        "Features/PebbleMedia/AttachedPhotoView.swift": 4,
        "Features/PebbleMedia/ExistingSnapRow.swift": 3,
        "Features/PebbleMedia/SnapImageView.swift": 1,
        "Features/Profile/Components/CollectionGlyphSection.swift": 1,
        "Features/Profile/Lists/CollectionsListView.swift": 1,
        "Features/Profile/Lists/SoulsListView.swift": 1,
        "Features/Profile/Sheets/CreateCollectionSheet.swift": 1,
        "Features/Profile/Sheets/CreateSoulSheet.swift": 1,
        "Features/Profile/Sheets/EditCollectionSheet.swift": 1,
        "Features/Profile/Sheets/EditSoulSheet.swift": 1,
        "Features/Profile/Sheets/SettingsSheet.swift": 4,
        "Features/Record/RecordStepScaffold.swift": 1,
        "Features/Record/Steps/RecordPhotoStep.swift": 3,
        "Features/Welcome/WelcomeView.swift": 2
    ]

    static let pebblesRoot: URL = {
        var url = URL(fileURLWithPath: #filePath)
        while url.lastPathComponent != "apps" { url.deleteLastPathComponent() }
        return url.appendingPathComponent("ios/Pebbles")
    }()

    /// Offending lines of one source. Text after `//` is ignored, and so is
    /// everything from the first `#Preview` on (preview fixtures fake server data).
    static func offendingLines(in source: String) -> [(line: Int, reason: String)] {
        var hits: [(line: Int, reason: String)] = []
        for (index, rawLine) in source.components(separatedBy: "\n").enumerated() {
            if rawLine.trimmingCharacters(in: .whitespaces).hasPrefix("#Preview") { break }
            let code = rawLine.components(separatedBy: "//")[0]
            let range = NSRange(code.startIndex..., in: code)
            if let rule = rules.first(where: { $0.regex.firstMatch(in: code, range: range) != nil }) {
                hits.append((index + 1, rule.reason))
            }
        }
        return hits
    }

    static func scan() throws -> [String: [(line: Int, reason: String)]] {
        var result: [String: [(line: Int, reason: String)]] = [:]
        let rootDepth = pebblesRoot.pathComponents.count
        for folder in scannedFolders {
            let base = pebblesRoot.appendingPathComponent(folder)
            guard let walker = FileManager.default.enumerator(at: base, includingPropertiesForKeys: nil) else {
                continue
            }
            for case let url as URL in walker where url.pathExtension == "swift" {
                let relative = url.pathComponents.dropFirst(rootDepth).joined(separator: "/")
                if exemptPrefixes.contains(where: { relative.hasPrefix($0) }) { continue }
                let hits = try offendingLines(in: String(contentsOf: url, encoding: .utf8))
                if !hits.isEmpty { result[relative] = hits }
            }
        }
        return result
    }

    // MARK: - Rules

    @Test("each rule catches its target and spares the allowed spellings", arguments: [
        (".foregroundStyle(.red)", true),
        ("return SIMD3<Float>(resolved.red, resolved.green, resolved.blue)", false),
        (".foregroundStyle(.secondary)", false),
        (".fill(Color.secondary.opacity(0.2))", true),
        (".background(Color.white)", true),
        (".shadow(color: .black.opacity(0.22), radius: 14, y: 4)", true),
        ("Color(hex: palette.primaryHex)", false),
        ("Color(hex: \"#A15C081A\") ?? .clear", true),
        ("Color(\"SystemMuted\")", true),
        ("UIColor(red: 0.42, green: 0.48, blue: 0.56, alpha: 1).setFill()", true),
        ("Color(.sRGB, red: 1, green: 1, blue: 1)", true),
        ("UIColor(named: \"AccentColor\")", true),
        ("let muted = UIColor.systemGray5", true),
        ("Button(role: .destructive) {", true),
        (".foregroundStyle(Color.pebbles.error)", false),
        ("proxy.backgroundColor = UIColor.pebbles(.surfaceContainerHighest)", false),
        (".tint(.primary) // was Color.white", false)
    ])
    func rule(line: String, offends: Bool) {
        #expect(Self.offendingLines(in: line).isEmpty != offends, "\(line)")
    }

    @Test("nothing from the first #Preview on is scanned")
    func previewIsExempt() {
        let source = "let ink = Color.pebbles.onSurface\n#Preview {\n    Text(\"x\").foregroundStyle(.red)\n}\n"
        #expect(Self.offendingLines(in: source).isEmpty)
    }

    // MARK: - Ratchet

    @Test("feature code names colours only through Color.pebbles")
    func ratchet() throws {
        let found = try Self.scan()
        var allowed = Self.debt
        for (path, entry) in Self.allowlist { allowed[path, default: 0] += entry.count }

        var problems: [String] = []
        for path in Set(found.keys).union(allowed.keys).sorted() {
            let hits = found[path] ?? []
            let limit = allowed[path] ?? 0
            if hits.count > limit {
                let lines = hits.map { "  \(path):\($0.line) — \($0.reason)" }.joined(separator: "\n")
                problems.append("\(path): \(hits.count) offending lines, \(limit) allowed — new literal.\n\(lines)")
            } else if hits.count < limit {
                problems.append("\(path): \(hits.count) offending lines, \(limit) allowed — lower the debt.")
            }
        }
        let current = found.keys.sorted().map { "\"\($0)\": \(found[$0]?.count ?? 0)," }.joined(separator: "\n")
        let summary = problems.joined(separator: "\n")
        #expect(problems.isEmpty, "\(summary)\n\nCurrent counts (allowlist + debt):\n\(current)")
    }
}
