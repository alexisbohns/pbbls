#!/bin/sh

# TestFlight's "What to Test" for the build Xcode Cloud just archived: the
# subject of the commit it built (main is squash-merged, so that is the PR
# title) and the branch it came from, so the build on the phone says which
# change it carries.
#
# Only an archive signed for App Store Connect sets CI_APP_STORE_SIGNED_APP_PATH;
# test and analyze actions skip this. Deliberately no `set -e`: the notes are a
# convenience, and a failure here must never stop the upload.
if [ -d "${CI_APP_STORE_SIGNED_APP_PATH}" ]; then
  # Scripts run from ci_scripts/; Xcode Cloud reads ../TestFlight.
  mkdir -p ../TestFlight
  {
    git log -1 --pretty=format:'%s'
    printf '\n\n%s · %s\n' "${CI_BRANCH:-${CI_PULL_REQUEST_SOURCE_BRANCH:-unknown branch}}" "$(printf '%s' "${CI_COMMIT}" | cut -c1-7)"
  } > ../TestFlight/WhatToTest.en-US.txt
fi
