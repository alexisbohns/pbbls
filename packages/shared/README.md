# @pbbls/shared

Data shared by every surface, copied per surface and pinned by a parity test (like `packages/icons/icons.json`).

## crisis-resources.json

The canonical EN/FR list of crisis lines per country, with a `verified_on` date.

- Edit only this file, then `cp` it byte-for-byte to the three copies:
  - `apps/web/lib/crisis/crisis-resources.json`
  - `apps/ios/Pebbles/Resources/crisis-resources.json`
  - `apps/android/app/src/main/assets/crisis-resources.json`
- Each surface has a test that compares its copy to this file byte for byte.
- Verification rule: every number must be checked against the official source (3114.fr, 988lifeline.org, 988.ca, samaritans.org, preventionsuicide.be, 143.ch, findahelpline.com) by the maintainer before merge, and `verified_on` updated.
