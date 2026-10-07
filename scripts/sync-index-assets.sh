#!/usr/bin/env bash
# Copy a freshly built and signed catalogue into the app.
#
#   ./scripts/sync-index-assets.sh [caminho-para-o-repo-do-indice]
#
# Three files travel together and must always come from the same build:
#   index.json              the catalogue
#   index.json.sig          its detached ECDSA signature
#   index-signing.pub.pem   the key the app will trust forever (until an app update)
#
# The public key is copied into the *source tree* on purpose: it is part of the
# app's identity, it must be reviewable in a diff, and it must never be fetched
# at runtime.
set -euo pipefail

INDEX_DIR="${1:-../index}"
HERE="$(cd "$(dirname "$0")" && pwd)"
ASSETS="$HERE/../app/src/main/assets"
TEST_RES="$HERE/../app/src/test/resources"

for file in index.json index.json.sig keys/index-signing.pub.pem; do
  [ -f "$INDEX_DIR/$file" ] || { echo "falta $INDEX_DIR/$file — corre tools/build-index.mjs e tools/sign-index.mjs primeiro" >&2; exit 1; }
done

mkdir -p "$ASSETS" "$TEST_RES"
cp "$INDEX_DIR/index.json" "$ASSETS/index.json"
cp "$INDEX_DIR/index.json.sig" "$ASSETS/index.json.sig"
cp "$INDEX_DIR/keys/index-signing.pub.pem" "$ASSETS/index-signing.pub.pem"

# Same bytes again under test resources: a unit test verifies the real signature
# with the real verifier, so a broken crypto path fails the build instead of
# shipping.
cp "$INDEX_DIR/index.json" "$TEST_RES/index.json"
cp "$INDEX_DIR/index.json.sig" "$TEST_RES/index.json.sig"
cp "$INDEX_DIR/keys/index-signing.pub.pem" "$TEST_RES/index-signing.pub.pem"

key_id="$(cd "$INDEX_DIR" && node tools/keys.mjs show 2>/dev/null | tail -1 | sed 's/.*: //')"
apps="$(node -e "console.log(JSON.parse(require('fs').readFileSync('$INDEX_DIR/index.json','utf8')).apps.length)")"
size="$(du -h "$ASSETS/index.json" | cut -f1)"

echo "sincronizado: $apps apps, $size, chave $key_id"
