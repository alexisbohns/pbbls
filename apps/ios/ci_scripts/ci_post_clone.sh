#!/bin/sh
set -e

# Xcode Cloud's first hook: everything the build needs that a clean clone lacks.
# The workflow itself (start conditions, archive, TestFlight group) lives in App
# Store Connect, not here — docs/ios-testflight-deploy.md is its only record.

# Install XcodeGen
brew install xcodegen

# Create Secrets.xcconfig from Xcode Cloud environment variables.
# Set PBBLS_SUPABASE_URL and PBBLS_SUPABASE_ANON_KEY as secret variables
# in the Xcode Cloud workflow (App Store Connect → Xcode Cloud → Environment).
# Note: Xcode Cloud forbids names starting with CI_ or TEST_RUNNER_.
#
# Fail here rather than ship: an empty value builds fine and only fatalErrors
# in AppEnvironment once the build is already on a tester's phone.
if [ -z "${PBBLS_SUPABASE_URL}" ] || [ -z "${PBBLS_SUPABASE_ANON_KEY}" ]; then
  echo "error: PBBLS_SUPABASE_URL and PBBLS_SUPABASE_ANON_KEY must both be set in the Xcode Cloud workflow's environment." >&2
  exit 1
fi

# xcconfig parses // as a comment, so https://… would land as just "https:",
# which URL(string:) still accepts — AppEnvironment would not catch it. Break
# the separator with $() the way Secrets.example.xcconfig does. A value that is
# already escaped has no // left, so it passes through unchanged.
SUPABASE_URL_XCCONFIG=$(printf '%s' "${PBBLS_SUPABASE_URL}" | sed 's#//#/$()/#g')

cat > "$CI_PRIMARY_REPOSITORY_PATH/apps/ios/Config/Secrets.xcconfig" <<EOF
SUPABASE_URL = ${SUPABASE_URL_XCCONFIG}
SUPABASE_ANON_KEY = ${PBBLS_SUPABASE_ANON_KEY}
EOF

# Generate Pebbles.xcodeproj from project.yml
cd "$CI_PRIMARY_REPOSITORY_PATH/apps/ios"
xcodegen generate

# Resolve Swift Package dependencies so Xcode Cloud's build step finds Package.resolved.
# Xcode Cloud disables automatic SPM resolution during the build phase and requires
# Package.resolved to exist at <project>/project.xcworkspace/xcshareddata/swiftpm/.
#
# That lock is two Xcode defaults, and they bind this xcodebuild too: with them
# on, the resolve meant to write the file refuses to run without it (exit 74,
# "a resolved file is required"), and the generated project is git-ignored, so
# a clean clone never has one. Lift them for this one resolve, then put them
# back so the archive builds from exactly the versions written here.
defaults read com.apple.dt.Xcode 2>/dev/null | grep -i package || true
defaults delete com.apple.dt.Xcode IDEPackageOnlyUseVersionsFromResolvedFile 2>/dev/null || true
defaults delete com.apple.dt.Xcode IDEDisableAutomaticPackageResolution 2>/dev/null || true
xcodebuild -resolvePackageDependencies \
  -project Pebbles.xcodeproj \
  -scheme Pebbles \
  -clonedSourcePackagesDirPath SourcePackages
defaults write com.apple.dt.Xcode IDEPackageOnlyUseVersionsFromResolvedFile -bool YES
defaults write com.apple.dt.Xcode IDEDisableAutomaticPackageResolution -bool YES
