import Foundation

/// The `payload` jsonb of a `pebble_drafts` row (M47) — a **partial**
/// `create_pebble` payload.
///
/// Keyed like the wire, not like `PebbleDraft`: the same shape is what the web
/// and Android composers write, so a draft saved on one surface resumes on the
/// others and publishing is a straight pass-through. See
/// `docs/superpowers/specs/2026-07-29-drafts-and-autosave-design.md` (D2).
///
/// Everything is optional, because a draft is partial by definition — "just a
/// name" is valid. Unset keys are **omitted**, never encoded as null: only
/// `description` and `glyph_id` treat null as meaningful, and a draft has no
/// prior value to clear.
///
/// Note what is deliberately absent: `PebbleDraft.valence` is stored decomposed
/// as `intensity` + `positiveness`, exactly as the wire wants it. That is why
/// neither `Valence` nor `Visibility` needed a `Codable` conformance for M47 —
/// this struct holds primitives only.
struct PebbleDraftPayload: Codable, Equatable {
    var name: String?
    var description: String?
    var happenedAt: Date?
    var intensity: Int?
    var positiveness: Int?
    var visibility: String?
    var emotionId: UUID?
    var domainIds: [UUID]?
    var soulIds: [UUID]?
    var collectionIds: [UUID]?
    var glyphId: UUID?
    var snaps: [SnapPayload]?

    struct SnapPayload: Codable, Equatable {
        let id: UUID
        let storagePath: String
        let sortOrder: Int

        enum CodingKeys: String, CodingKey {
            case id
            case storagePath = "storage_path"
            case sortOrder   = "sort_order"
        }
    }

    enum CodingKeys: String, CodingKey {
        case name
        case description
        case happenedAt = "happened_at"
        case intensity
        case positiveness
        case visibility
        case emotionId     = "emotion_id"
        case domainIds     = "domain_ids"
        case soulIds       = "soul_ids"
        case collectionIds = "collection_ids"
        case glyphId       = "glyph_id"
        case snaps
    }

    /// `encodeIfPresent` throughout — that is the "omit unset keys" rule.
    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encodeIfPresent(name, forKey: .name)
        try container.encodeIfPresent(description, forKey: .description)
        // Whole seconds, matching PebbleCreatePayload byte-for-byte so the publish
        // path is unchanged and the other surfaces parse it happily.
        try container.encodeIfPresent(
            happenedAt.map { ISO8601Flexible.string(from: $0) }, forKey: .happenedAt
        )
        try container.encodeIfPresent(intensity, forKey: .intensity)
        try container.encodeIfPresent(positiveness, forKey: .positiveness)
        try container.encodeIfPresent(visibility, forKey: .visibility)
        try container.encodeIfPresent(emotionId, forKey: .emotionId)
        try container.encodeIfPresent(domainIds, forKey: .domainIds)
        try container.encodeIfPresent(soulIds, forKey: .soulIds)
        try container.encodeIfPresent(collectionIds, forKey: .collectionIds)
        try container.encodeIfPresent(glyphId, forKey: .glyphId)
        try container.encodeIfPresent(snaps, forKey: .snaps)
    }

    /// Tolerant by design: a payload written by another surface (or an older
    /// build) must never fail to decode. `happened_at` goes through
    /// `ISO8601Flexible` because web writes milliseconds and Postgres writes
    /// microseconds — a fixed-precision formatter silently nils both (#651). A
    /// genuinely unparseable value is still dropped rather than thrown, leaving
    /// hydration to fall back to "now".
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        name = try container.decodeIfPresent(String.self, forKey: .name)
        description = try container.decodeIfPresent(String.self, forKey: .description)
        happenedAt = try container.decodeIfPresent(String.self, forKey: .happenedAt)
            .flatMap { ISO8601Flexible.parse($0) }
        intensity = try container.decodeIfPresent(Int.self, forKey: .intensity)
        positiveness = try container.decodeIfPresent(Int.self, forKey: .positiveness)
        visibility = try container.decodeIfPresent(String.self, forKey: .visibility)
        emotionId = try container.decodeIfPresent(UUID.self, forKey: .emotionId)
        domainIds = try container.decodeIfPresent([UUID].self, forKey: .domainIds)
        soulIds = try container.decodeIfPresent([UUID].self, forKey: .soulIds)
        collectionIds = try container.decodeIfPresent([UUID].self, forKey: .collectionIds)
        glyphId = try container.decodeIfPresent(UUID.self, forKey: .glyphId)
        snaps = try container.decodeIfPresent([SnapPayload].self, forKey: .snaps)
    }

    init() {}
}

// MARK: - Projections

extension PebbleDraftPayload {
    /// Composer state → draft payload. Empty strings and empty arrays are
    /// omitted so `isEmpty` below can tell "untouched" from "deliberately blank".
    ///
    /// `formSnap` is passed separately because snap state lives on the
    /// `SnapUploadCoordinator`, not on the draft — same split as
    /// `PebbleCreatePayload`. Pass `nil` for the local autosave snapshot, which
    /// carries no media (D3).
    init(from draft: PebbleDraft, formSnap: FormSnap?, userId: UUID?) {
        self.init()
        // whitespacesAndNewlines, not .whitespaces: the description field is
        // multiline, so a draft holding only newlines is still an empty draft.
        // (PebbleCreatePayload trims with .whitespaces — worth aligning one day.)
        let trimmedName = draft.name.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedDescription = draft.description.trimmingCharacters(in: .whitespacesAndNewlines)
        name = trimmedName.isEmpty ? nil : trimmedName
        description = trimmedDescription.isEmpty ? nil : trimmedDescription
        happenedAt = draft.happenedAt
        // Both or neither: a nil valence means the user has not picked one, and
        // hydration must not invent an intensity for them.
        intensity = draft.valence?.intensity
        positiveness = draft.valence?.positiveness
        visibility = draft.visibility.rawValue
        emotionId = draft.emotionId
        domainIds = draft.domainId.map { [$0] }
        soulIds = draft.soulIds.isEmpty ? nil : draft.soulIds
        collectionIds = draft.collectionId.map { [$0] }
        glyphId = draft.glyphId
        snaps = Self.snapPayloads(for: formSnap, userId: userId)
    }

    private static func snapPayloads(for formSnap: FormSnap?, userId: UUID?) -> [SnapPayload]? {
        switch formSnap {
        case .none:
            return nil
        case .pending(let snap):
            // The bytes are already in Storage (upload happens at pick time), so
            // the descriptor alone is enough to carry the photo across a draft.
            guard let userId else { return nil }
            return [SnapPayload(id: snap.id, storagePath: snap.storagePrefix(userId: userId), sortOrder: 0)]
        case .existing(let id, let storagePath):
            return [SnapPayload(id: id, storagePath: storagePath, sortOrder: 0)]
        }
    }

    /// True when nothing a user would recognise as their own input is present.
    /// `happenedAt` / `intensity` / `positiveness` / `visibility` never count:
    /// they are always defaulted, and counting them would make every freshly
    /// opened composer look like an unsaved draft.
    var isEmpty: Bool {
        name == nil
        && description == nil
        && emotionId == nil
        && glyphId == nil
        && (domainIds?.isEmpty ?? true)
        && (soulIds?.isEmpty ?? true)
        && (collectionIds?.isEmpty ?? true)
        && (snaps?.isEmpty ?? true)
    }

    /// The snap descriptor to seed onto a `SnapUploadCoordinator` on resume, if
    /// this draft carries one.
    var existingSnap: (id: UUID, storagePath: String)? {
        guard let snap = snaps?.first else { return nil }
        return (snap.id, snap.storagePath)
    }
}

extension PebbleDraft {
    /// Ids the composer can verify synchronously from `ReferenceDataService`.
    /// Souls and collections are user-owned and deletable, so a draft can outlive
    /// them; emotions and domains are seeded reference rows and pass through
    /// untouched.
    ///
    /// Glyphs are deliberately absent: usability is `can_use_glyph(id, user)`
    /// (own ∪ system ∪ entitled), the same predicate `create_pebble` enforces,
    /// and it can only be answered server-side. The composer verifies it
    /// asynchronously after hydrating — see `CreatePebbleSheet.verifyGlyph()`.
    struct KnownIds {
        let soulIds: Set<UUID>
        let collectionIds: Set<UUID>
    }

    /// Draft payload → composer state, dropping references that no longer
    /// resolve (D7). Sanitizing here rather than at publish means a deleted soul
    /// costs the user a chip, not an opaque foreign-key error at the worst
    /// possible moment.
    init(payload: PebbleDraftPayload, known: KnownIds) {
        self.init()
        if let name = payload.name { self.name = name }
        if let description = payload.description { self.description = description }
        // Restored verbatim (D6): for a quick capture the moment captured IS the
        // moment it happened, so publishing later must not move it.
        if let happenedAt = payload.happenedAt { self.happenedAt = happenedAt }
        if let positiveness = payload.positiveness, let intensity = payload.intensity {
            self.valence = Valence.from(positiveness: positiveness, intensity: intensity)
        }
        if let visibility = payload.visibility {
            self.visibility = Visibility(rawValue: visibility) ?? .secret
        }
        self.emotionId = payload.emotionId
        self.domainId = payload.domainIds?.first
        self.soulIds = (payload.soulIds ?? []).filter { known.soulIds.contains($0) }
        self.collectionId = payload.collectionIds?.first.flatMap {
            known.collectionIds.contains($0) ? $0 : nil
        }
        // Kept as-is here; `can_use_glyph` is checked asynchronously by the
        // composer, which clears it if the glyph is no longer usable.
        self.glyphId = payload.glyphId
    }

    /// Relaxed counterpart to `isValid` (D5): what "Save as draft" allows.
    /// `isValid` must stay strict because `PebbleCreatePayload.init`
    /// preconditions on it and force-unwraps the mandatory fields.
    ///
    /// Takes `formSnap` so a photo-only draft counts as savable — the photo is
    /// real input, and it lives outside `PebbleDraft`.
    func isSavableAsDraft(formSnap: FormSnap?, userId: UUID?) -> Bool {
        !PebbleDraftPayload(from: self, formSnap: formSnap, userId: userId).isEmpty
    }
}
