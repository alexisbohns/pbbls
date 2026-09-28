#!/usr/bin/env python3
"""Writes core/designsystem/ColorSchemes.kt from the M3-evo theme export (#853, #990).

Usage, from apps/android:
    python3 scripts/generate-color-schemes.py [path/to/export.json]

The export defaults to design/pbbls-m3_evo-theme.json. Its six `schemes` become
`ColorScheme` values; each entry of `extendedColors` becomes six `ColorFamily`
values read from `extendedSchemes` (M3's ColorScheme has no slot for custom
colours). Web generates its CSS from the Kotlin this writes (apps/web
`npm run generate:m3`), so regenerate both in the same change.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / 'design' / 'pbbls-m3_evo-theme.json'
DST = ROOT / 'app/src/main/kotlin/app/pbbls/android/core/designsystem/ColorSchemes.kt'

SCHEMES = [
    ('LightScheme', 'light', 'lightColorScheme'),
    ('LightMediumContrastScheme', 'light-medium-contrast', 'lightColorScheme'),
    ('LightHighContrastScheme', 'light-high-contrast', 'lightColorScheme'),
    ('DarkScheme', 'dark', 'darkColorScheme'),
    ('DarkMediumContrastScheme', 'dark-medium-contrast', 'darkColorScheme'),
    ('DarkHighContrastScheme', 'dark-high-contrast', 'darkColorScheme'),
]
SKIP = {'shadow'}  # in the export, not a ColorScheme role


def literal(hex_value: str) -> str:
    return f'Color(0xFF{hex_value.lstrip("#").upper()})'


def camel(name: str) -> str:
    """'Sand' -> 'sand', 'Warm Sand' -> 'warmSand'."""
    words = re.split(r'[^A-Za-z0-9]+', name.strip())
    return words[0].lower() + ''.join(w.capitalize() for w in words[1:])


def pascal(name: str) -> str:
    c = camel(name)
    return c[0].upper() + c[1:]


d = json.loads(SRC.read_text())
extended = d.get('extendedColors', [])

out = ['package app.pbbls.android.core.designsystem', '',
       'import androidx.compose.material3.ColorScheme',
       'import androidx.compose.material3.darkColorScheme',
       'import androidx.compose.material3.lightColorScheme',
       'import androidx.compose.ui.graphics.Color',
       'import androidx.compose.ui.graphics.toArgb',
       'import java.util.Locale', '',
       '// GENERATED from the M3-evo Material Theme Builder export (seed #CE7E8A,',
       f"// {d['description'].splitlines()[-1]}) for #853. Regenerate with",
       '// apps/android/scripts/generate-color-schemes.py (#990);',
       '// do not hand-edit a value. This file is the only place a Color(0x…) literal',
       '// may live outside tests — ThemeLiteralsTest enforces it from Part 7.', '']

for name, key, fn in SCHEMES:
    out.append(f'internal val {name}: ColorScheme =')
    out.append(f'    {fn}(')
    for role, hex_value in d['schemes'][key].items():
        if role in SKIP:
            continue
        out.append(f'        {role} = {literal(hex_value)},')
    out.append('    )')
    out.append('')

for color in extended:
    family = camel(color['name'])
    for name, key, _ in SCHEMES:
        roles = d['extendedSchemes'][key]
        prefix = name.removesuffix('Scheme')
        out.append(f"/** {color['name']} (custom colour, not harmonized) for [{name}]. */")
        out.append(f'internal val {prefix}{pascal(color["name"])}: ColorFamily =')
        out.append('    ColorFamily(')
        out.append(f'        color = {literal(roles[family])},')
        out.append(f'        onColor = {literal(roles["on" + pascal(color["name"])])},')
        out.append(f'        colorContainer = {literal(roles[family + "Container"])},')
        out.append(f'        onColorContainer = {literal(roles["on" + pascal(color["name"]) + "Container"])},')
        out.append('    )')
        out.append('')

out += [
    '/**',
    ' * Ink for the Google sign-in capsule, which is a pinned white surface under',
    " * Google's branding rules and must not follow the theme (the old iOS",
    ' * `onLight` token). 11.2:1 on white.',
    ' */',
    'internal val GoogleCapsuleInk = Color(0xFF4A3639)', '',
    '/** One of the six static schemes. Wallpaper schemes bypass this entirely. */',
    'internal fun pebblesColorScheme(',
    '    dark: Boolean,',
    '    contrast: ContrastLevel,',
    '): ColorScheme =',
    '    when (contrast) {',
    '        ContrastLevel.STANDARD -> if (dark) DarkScheme else LightScheme',
    '        ContrastLevel.MEDIUM -> if (dark) DarkMediumContrastScheme else LightMediumContrastScheme',
    '        ContrastLevel.HIGH -> if (dark) DarkHighContrastScheme else LightHighContrastScheme',
    '    }', '',
]

for color in extended:
    p = pascal(color['name'])
    out += [
        f"/** {color['name']} for one of the six static schemes. Wallpaper colour keeps it too. */",
        f'internal fun pebbles{p}(',
        '    dark: Boolean,',
        '    contrast: ContrastLevel,',
        '): ColorFamily =',
        '    when (contrast) {',
        f'        ContrastLevel.STANDARD -> if (dark) Dark{p} else Light{p}',
        f'        ContrastLevel.MEDIUM -> if (dark) DarkMediumContrast{p} else LightMediumContrast{p}',
        f'        ContrastLevel.HIGH -> if (dark) DarkHighContrast{p} else LightHighContrast{p}',
        '    }', '',
    ]

out += [
    '// Hand-written, not from the export: the generator script emits this block',
    '// verbatim so a regeneration keeps it. Keep the two in step.', '',
    '/** `#RRGGBB`, alpha dropped — the SVG pipeline misparses 8-digit hex. */',
    'internal fun Color.toRgbHex(): String = String.format(Locale.ROOT, "#%06X", toArgb() and 0xFFFFFF)', '',
]

DST.write_text('\n'.join(out))
print(DST.relative_to(ROOT), sum(1 for line in out if 'Color(0xFF' in line), 'literals')
