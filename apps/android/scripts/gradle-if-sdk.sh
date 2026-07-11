#!/usr/bin/env bash
# Runs a Gradle task only when an Android SDK is resolvable (D13).
#
# The dev container and most non-CI environments have no Android SDK, so root
# `npm run build` / `turbo build` must not fail there: no SDK → one loud warning
# line, exit 0. The authoritative Android build gate is the path-filtered
# .github/workflows/android.yml, which always has an SDK — the masking risk of
# skipping here is accepted because that workflow is the real check.
set -euo pipefail

app_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

sdk_dir=""
if [[ -n "${ANDROID_HOME:-}" ]]; then
  sdk_dir="$ANDROID_HOME"
elif [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
  sdk_dir="$ANDROID_SDK_ROOT"
elif [[ -f "$app_dir/local.properties" ]]; then
  sdk_dir="$(grep -E '^sdk\.dir=' "$app_dir/local.properties" | head -1 | cut -d= -f2-)"
fi

if [[ -z "$sdk_dir" || ! -d "$sdk_dir" ]]; then
  echo "⚠️  @pbbls/android: no Android SDK found (ANDROID_HOME / ANDROID_SDK_ROOT / local.properties) — skipping 'gradlew $*'. Build via Android Studio or the android.yml CI workflow." >&2
  exit 0
fi

exec "$app_dir/gradlew" "$@"
