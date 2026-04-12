#!/bin/sh
set -e

# Install XcodeGen
brew install xcodegen

# Create Secrets.xcconfig from Xcode Cloud environment variables.
# Set PBBLS_SUPABASE_URL and PBBLS_SUPABASE_ANON_KEY as secret variables
# in the Xcode Cloud workflow (App Store Connect → Xcode Cloud → Environment).
# Note: Xcode Cloud forbids names starting with CI_ or TEST_RUNNER_.
cat > "$CI_PRIMARY_REPOSITORY_PATH/apps/ios/Config/Secrets.xcconfig" <<EOF
SUPABASE_URL = ${PBBLS_SUPABASE_URL}
SUPABASE_ANON_KEY = ${PBBLS_SUPABASE_ANON_KEY}
EOF

# Generate Pebbles.xcodeproj from project.yml
cd "$CI_PRIMARY_REPOSITORY_PATH/apps/ios"
xcodegen generate

# Resolve Swift Package dependencies so Xcode Cloud's build step finds Package.resolved.
# Xcode Cloud disables automatic SPM resolution during the build phase and requires
# Package.resolved to exist at <project>/project.xcworkspace/xcshareddata/swiftpm/.
xcodebuild -resolvePackageDependencies \
  -project Pebbles.xcodeproj \
  -scheme Pebbles \
  -clonedSourcePackagesDirPath SourcePackages
