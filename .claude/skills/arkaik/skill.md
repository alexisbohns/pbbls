---
name: arkaik
description: >
  Maintain the Arkaik product graph map — add, update, or remove nodes and edges
  in the ProjectBundle JSON that describes Pebbles' screens, flows, data models,
  and API endpoints. Use this skill whenever you create, rename, move, or delete
  a view/screen, route, data model, or API endpoint in the codebase. Also use it
  when changing a feature's status (idea → development → live), adding a new user
  journey, or restructuring navigation. If your code change touches product
  architecture, this skill applies — even if no one explicitly asked you to
  update the map.
---

# Arkaik Map Maintenance

You are maintaining an **Arkaik ProjectBundle** — a JSON file that describes the
product architecture of Pebbles as a graph of nodes (screens, flows, data models,
API endpoints) and edges (relationships between them).

The map lives at `apps/web/docs/arkaik/bundle.json` in the repository. If it
doesn't exist at that path yet, check the project root and `docs/` for any
`*arkaik*.json` file. If you find one elsewhere, use it where it is — don't move
files without the user's approval.

## When to Update the Map

Update the map as a side-effect of your main work whenever you:

- **Add** a new screen, page, route, component, model, or endpoint
- **Remove** or deprecate a feature, screen, or endpoint
- **Rename** a view, model, or route
- **Change status** of a feature (e.g., moving from `idea` to `development`)
- **Restructure** navigation or user flows
- **Add or change** API contracts

Do NOT regenerate the entire map. Make **surgical patches** — touch only the
nodes and edges affected by your change. This keeps diffs reviewable and avoids
accidental regressions.

## How to Update

### 1. Read the current map

Always read the map file first. Parse it and identify the relevant nodes/edges
before making changes.

### 2. Decide what to change

Map your code change to graph operations:

| Code change | Graph operation |
|---|---|
| New screen/page | Add a `V-` view node + `displays` edges to its data models + `calls` edges to its APIs |
| New route/endpoint | Add an `API-` node + `queries` edges to the data models it reads/writes |
| New model/table | Add a `DM-` node |
| New user journey | Add a `F-` flow node with a playlist + `composes` edges to all views/sub-flows in the playlist |
| Screen added to a flow | Add entry to the flow's playlist + a `composes` edge |
| Feature removed | Remove the node + all edges referencing it + remove from any playlists |
| Status change | Update the node's `status` field |
| Rename | Update the node's `title` (keep the `id` stable to avoid breaking edges) |

### 3. Apply the change

Edit the JSON using the Edit tool for surgical changes, or Write for larger
restructuring. Follow these rules strictly:

**Node rules:**
- IDs are prefixed by species: `F-` (flow), `V-` (view), `DM-` (data-model), `API-` (api-endpoint)
- IDs use lowercase kebab-case after the prefix (e.g., `V-user-profile`)
- Every `node.project_id` must match `project.id`
- `platforms` must contain at least one of: `"web"`, `"ios"`, `"android"`
- Flow nodes must have `metadata.playlist` with at least one entry

**Edge rules:**
- Edge IDs follow the pattern `e-{source_id}-{target_id}`
- Every `source_id` and `target_id` must reference existing node IDs
- Edge type semantics:
  - `composes`: flow -> view, flow -> flow (sub-flow), view -> flow (triggers)
  - `calls`: view -> api-endpoint, flow -> api-endpoint
  - `displays`: view -> data-model
  - `queries`: api-endpoint -> data-model
- Every view/flow referenced in a playlist MUST also have a `composes` edge

**Playlist rules:**
- Entry types: `view` (with `view_id`), `flow` (with `flow_id`), `condition` (with `label`, `if_true`, `if_false`), `junction` (with `label`, `cases`)
- All `view_id` / `flow_id` values must reference existing nodes
- No cycles — a flow cannot contain itself directly or indirectly

### 4. Validate

After editing, run the validation script:

```bash
node <skill-path>/scripts/validate-bundle.js <path-to-bundle.json>
```

If validation fails, fix the errors before committing. Common issues:
- Forgetting to add a `composes` edge when adding a view to a playlist
- Stale edge references after removing a node
- Missing `project_id` on new nodes

### 5. Update timestamps

Set `project.updated_at` to the current ISO 8601 timestamp.

## Full Schema Reference

For the complete TypeScript types, allowed values for `status`, `species`,
`edge_type`, `platform`, and detailed playlist structures, read:

```
<skill-path>/references/schema.md
```

Consult this reference whenever you're unsure about a field's type or allowed
values. It is the source of truth.

## Bootstrap: Generating a Map from Scratch

If the project doesn't have a map yet and the user asks you to create one:

1. Scan the codebase for routes/pages, models, and API endpoints
2. Read any product specs or PRDs in the repo
3. Generate the full ProjectBundle following the schema reference
4. Validate with the bundled script
5. Save to `docs/arkaik/bundle.json` (or ask the user where they want it)

This is the only case where generating the full map is appropriate. For all
subsequent changes, use surgical patches.