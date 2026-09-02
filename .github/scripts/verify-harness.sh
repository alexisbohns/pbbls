#!/usr/bin/env bash
# Runs one contract harness, appends a row to the job summary, and records the
# label of a failing harness so the nightly tracking issue can name it.
#
# Why this exists rather than calling npm directly from the workflow: the job
# summary needs the harness's assertion counts, which means capturing stdout,
# and GitHub's default shell is `bash -e {0}` with NO pipefail — piping the
# harness through `tee` there would report success for a failing harness and
# silently defeat the gate. `PIPESTATUS[0]` is the harness's own status
# regardless of pipefail, so the exit code cannot be lost.
#
# Usage: verify-harness.sh <label> <npm-script>
set -uo pipefail

label="$1"
script="$2"

npm run --silent "$script" --workspace=packages/supabase 2>&1 | tee "${RUNNER_TEMP:-/tmp}/harness-out.txt"
status="${PIPESTATUS[0]}"

# Every harness ends with `Summary: passed=N failed=M`. A missing line means it
# died before its own summary (a network failure, a bad credential), which is a
# failure whose counts we honestly do not know.
counts="$(grep -oE 'passed=[0-9]+ failed=[0-9]+' "${RUNNER_TEMP:-/tmp}/harness-out.txt" | tail -1 || true)"
if [ -n "$counts" ]; then
  passed="${counts#passed=}"; passed="${passed%% *}"
  failed="${counts##*failed=}"
  asserts="$passed/$((passed + failed))"
else
  asserts="—"
fi

if [ "$status" -eq 0 ]; then
  result="pass"
else
  result="**FAIL**"
  echo "$label" >> "${HARNESS_FAILURES:-/dev/null}"
fi

# Rows accumulate in a ledger that ONE later step turns into the summary table.
# Writing straight to $GITHUB_STEP_SUMMARY here would not work: that file is
# unique per step, and a table whose header and rows land in five different
# step summaries only renders as a table if GitHub happens to concatenate them
# without a blank line. One writer, one table, no bet.
echo "| $label | $result | $asserts |" >> "${HARNESS_ROWS:-/dev/null}"

exit "$status"
