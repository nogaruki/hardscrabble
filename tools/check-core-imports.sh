#!/usr/bin/env bash
# ET-Q-06: the pure formula layer (dev.mite.core) never depends on the game or the loader.
set -euo pipefail
cd "$(dirname "$0")/.."
if grep -rnE '^import (static )?net\.(minecraft|neoforged)\.' src/main/java/dev/mite/core; then
    echo "FAIL: dev.mite.core imports net.minecraft or net.neoforged (ET-Q-06)" >&2
    exit 1
fi
echo "OK: core has no engine imports"
