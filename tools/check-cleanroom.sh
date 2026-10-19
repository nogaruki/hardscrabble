#!/usr/bin/env bash
# ET-Q-07: no compiled MITE code, no analysis workspace and no denylisted identifier in the tracked files.
# This guards the clean-room process (docs/02 section 13), it does not replace it.
set -euo pipefail
cd "$(dirname "$0")/.."
fail=0
if git ls-files | grep -E '\.(class|jar)$' | grep -v '^gradle/wrapper/gradle-wrapper\.jar$'; then
    echo "FAIL: compiled classes or jars are tracked" >&2; fail=1
fi
if git ls-files | grep -E '(^|/)(original|re-workspace)/'; then
    echo "FAIL: original/ or re-workspace/ is tracked" >&2; fail=1
fi
words=$(grep -vE '^\s*(#|$)' tools/cleanroom-denylist.txt | paste -sd'|')
if git ls-files -z | grep -zv -e '^tools/cleanroom-denylist\.txt$' -e '^docs/' | xargs -0 grep -nIwE "$words"; then
    echo "FAIL: denylisted identifier found (tools/cleanroom-denylist.txt)" >&2; fail=1
fi
# ET-A-07 / ENF-SEC (Q-5): the mod makes no network call of its own (multiplayer goes through the game's connection only).
if git ls-files -z 'src/*.java' | xargs -0 grep -nE 'java\.net\.|HttpURLConnection|HttpClient|new (Server)?Socket\(|DatagramSocket'; then
    echo "FAIL: network API used in the mod code (ET-A-07)" >&2; fail=1
fi
# ENF-QUA-01: data is generated. Only the documented vanilla overrides (data/minecraft/, see DEVELOPMENT.md) are hand-written.
if find src/main/resources -name '*.json' ! -path '*/data/minecraft/*' ! -name 'hardscrabble.mixins.json' | grep .; then
    echo "FAIL: hand-written JSON outside data/minecraft/ (use datagen)" >&2; fail=1
fi
[ "$fail" = 0 ] && echo "OK: clean-room checks passed"
exit "$fail"
