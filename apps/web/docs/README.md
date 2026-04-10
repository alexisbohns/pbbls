# docs/

Centralized documentation content for Pebbles. These markdown files are designed to be:

- **Served by the webapp** as static legal/support pages
- **Consumed by the iOS app** via webview
- **Maintained by code agents** using the machine-readable manifest

## Folder structure

```
docs/
├── index.json            ← manifest (page metadata, ordering, locales)
├── README.md             ← this file
├── arkaik/
│   └── bundle.json       ← product architecture map (separate concern)
├── legal-notice/
│   ├── en.md
│   └── fr.md
├── terms/
│   ├── en.md
│   └── fr.md
└── privacy/
    ├── en.md
    └── fr.md
```

Each page lives in its own folder. Each folder contains one markdown file per supported locale.

## Manifest schema (`index.json`)

The manifest is the single source of truth for which pages exist and how they are categorized.

```jsonc
{
  "$schema": "docs-manifest-v1",    // schema version identifier
  "locales": ["en", "fr"],          // supported locale codes (ISO 639-1)
  "pages": [
    {
      "slug": "privacy",            // unique page identifier, matches folder name
      "category": "legal",          // grouping key (e.g. "legal", "support")
      "order": 3,                   // display order within category
      "titles": {
        "en": "Privacy Policy",     // human-readable title per locale
        "fr": "Politique de confidentialité"
      },
      "path": "docs/privacy/"       // relative path to the page folder
    }
  ]
}
```

| Field      | Type              | Description                                      |
|------------|-------------------|--------------------------------------------------|
| `slug`     | `string`          | Unique identifier. Must match the folder name.   |
| `category` | `string`          | Grouping key for navigation (e.g. `"legal"`).    |
| `order`    | `number`          | Sort order within the category.                  |
| `titles`   | `Record<string, string>` | Locale-keyed display titles.              |
| `path`     | `string`          | Relative path from repo root to the page folder. |

## Locale conventions

- Locale codes follow **ISO 639-1** (two-letter): `en`, `fr`, etc.
- Each page folder contains one file per locale, named `{locale}.md` (e.g. `en.md`, `fr.md`).
- All supported locales are listed in the `locales` array in `index.json`.
- When adding a new locale, add the code to `locales` and create the corresponding `.md` file in every page folder.

## Markdown frontmatter

Every markdown file must include YAML frontmatter with these fields:

```yaml
---
title: Privacy Policy    # page title in the file's locale
locale: en               # ISO 639-1 locale code
slug: privacy            # must match the parent folder name and manifest slug
---
```

The body contains section headings (`##`) as structural placeholders. Content is filled in under each heading.

## Adding a new page

1. **Create the folder**: `docs/{slug}/`
2. **Add locale files**: create `en.md` and `fr.md` (and any other supported locales) with frontmatter and section headings
3. **Update the manifest**: add an entry to the `pages` array in `docs/index.json` with the correct slug, category, order, titles, and path
4. **Verify**: ensure the slug matches the folder name and all supported locales have a file

## Contribution guidelines

- **Do not** rename or move `arkaik/bundle.json` — it is managed separately by the Arkaik skill.
- **Keep stubs minimal**: section headings only until content is reviewed and approved.
- **Preserve frontmatter**: every markdown file must have valid YAML frontmatter. Consumers depend on it for routing and metadata.
- **One folder per page**: do not nest pages or combine multiple pages in a single folder.
- **Manifest is the source of truth**: if a page exists on disk but is not in `index.json`, it will not be served. Always update the manifest when adding or removing pages.
