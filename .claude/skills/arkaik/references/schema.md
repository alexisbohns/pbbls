# Arkaik ProjectBundle Schema Reference

## Table of Contents

1. [Enums & Type Aliases](#enums--type-aliases)
2. [Playlist Entries](#playlist-entries)
3. [Node](#node)
4. [Edge](#edge)
5. [Project](#project)
6. [ProjectBundle](#projectbundle)
7. [Edge Type Semantics](#edge-type-semantics)
8. [ID Conventions](#id-conventions)
9. [Validation Checklist](#validation-checklist)

---

## Enums & Type Aliases

```typescript
type Species = "flow" | "view" | "data-model" | "api-endpoint";
type Status = "idea" | "backlog" | "prioritized" | "development" | "releasing" | "live" | "archived" | "blocked";
type Platform = "web" | "ios" | "android";
type EdgeType = "composes" | "calls" | "displays" | "queries";
```

---

## Playlist Entries

Flows orchestrate views through an ordered playlist. Each entry is one of:

```typescript
type PlaylistEntry =
  | { type: "view"; view_id: string }
  | { type: "flow"; flow_id: string }
  | { type: "condition"; label: string; if_true: PlaylistEntry[]; if_false: PlaylistEntry[] }
  | { type: "junction"; label: string; cases: JunctionCase[] };

interface JunctionCase {
  label: string;
  entries: PlaylistEntry[];
}

interface FlowPlaylist {
  entries: PlaylistEntry[];
}
```

**Condition** is a binary branch (yes/no question). The `label` is a question
(e.g., "Email verified?"), and `if_true` / `if_false` contain the entries for
each branch. Either branch can be empty `[]` to mean "skip."

**Junction** is a multi-way branch. The `label` is a question (e.g., "What
action?"), and `cases` is an array of labeled branches, each with its own
entries.

Every `view_id` and `flow_id` in playlist entries must reference node IDs that
exist in the bundle's `nodes` array.

---

## Node

```typescript
interface NodeMetadata {
  stage?: "beta" | "monitoring" | "deprecated";
  playlist?: FlowPlaylist;                           // Required for flow nodes
  platformNotes?: Partial<Record<Platform, string>>;
  platformStatuses?: Partial<Record<Platform, Status>>; // Views only
}

interface Node {
  id: string;                // Unique. Prefixed by species (see ID Conventions)
  project_id: string;        // Must exactly match project.id
  species: Species;
  title: string;             // 2-5 words, descriptive but concise
  description?: string;      // 1 sentence
  status: Status;
  platforms: Platform[];     // At least one value
  metadata?: NodeMetadata;   // Required for flows (must include playlist)
}
```

---

## Edge

```typescript
interface Edge {
  id: string;                // Convention: e-{source_id}-{target_id}
  project_id: string;        // Must exactly match project.id
  source_id: string;         // Must reference an existing node ID
  target_id: string;         // Must reference an existing node ID
  edge_type: EdgeType;
  metadata?: Record<string, unknown>;
}
```

---

## Project

```typescript
interface Project {
  id: string;
  title: string;
  description?: string;
  root_node_id?: string;     // Should reference an existing node
  metadata?: { view_card_variant?: "compact" | "large" };
  created_at: string;        // ISO 8601 (e.g., "2026-01-01T00:00:00.000Z")
  updated_at: string;        // ISO 8601
  archived_at?: string | null;
}
```

---

## ProjectBundle

```typescript
interface ProjectBundle {
  project: Project;
  nodes: Node[];
  edges: Edge[];
}
```

---

## Edge Type Semantics

| Edge type | Valid source → target | Meaning |
|---|---|---|
| `composes` | flow → view | Flow contains this view in its playlist |
| `composes` | flow → flow | Flow contains this sub-flow in its playlist |
| `composes` | view → flow | View triggers/navigates to this flow |
| `composes` | view → view | View contains or navigates to this view |
| `calls` | view → api-endpoint | View calls this API |
| `calls` | flow → api-endpoint | Flow calls this API |
| `displays` | view → data-model | View displays data from this model |
| `queries` | api-endpoint → data-model | API reads or writes this model |

Any other source → target combination for a given edge type is invalid.

---

## ID Conventions

| Species | Prefix | Example |
|---|---|---|
| flow | `F-` | `F-record-pebble` |
| view | `V-` | `V-pebble-detail` |
| data-model | `DM-` | `DM-emotion-pearl` |
| api-endpoint | `API-` | `API-create-pebble` |

After the prefix, use lowercase kebab-case. Keep IDs short but meaningful —
they appear in the Arkaik UI.

Edge IDs: `e-{source_id}-{target_id}` (e.g., `e-V-home-F-onboarding`).

---

## Validation Checklist

Before saving any changes, verify:

1. All node IDs are unique
2. All node IDs have the correct species prefix
3. All `node.project_id` values match `project.id`
4. All `edge.source_id` and `edge.target_id` reference existing node IDs
5. All `edge.project_id` values match `project.id`
6. `project.root_node_id` references an existing node
7. All `view_id` / `flow_id` in playlists reference existing node IDs
8. Every view/flow referenced in a playlist has a corresponding `composes` edge
9. No playlist cycles (a flow does not contain itself directly or indirectly)
10. All flow nodes have `metadata.playlist` with at least one entry
11. All `platforms` arrays have at least one value
12. Edge types follow the valid source → target patterns
13. `updated_at` timestamp is current