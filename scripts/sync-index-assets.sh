#!/usr/bin/env bash
# Copy a freshly built and signed catalogue into the app.
#
#   ./scripts/sync-index-assets.sh [caminho-para-o-repo-do-indice]
#
# Three files travel together and must always come from the same build:
#   index.json              the catalogue
#   index.json.sig          its detached ECDSA signature
#   index-signing.pub.pem   the key the app trusts, and the one it calls principal
#
# The public keys are copied into the *source tree* on purpose: they are part of the
# app's identity, they must be reviewable in a diff, and they must never be fetched
# at runtime.
#
# Todas as `keys/*.pub.pem` são copiadas, e não só a principal. É isso que torna uma
# rotação possível: um build que traga as duas chaves aceita o catálogo assinado por
# qualquer delas, e é o nome do ficheiro que diz qual é a principal — as outras
# chamam-se `index-signing-<nome>.pub.pem`.
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

# Same bytes again under test resources: a unit test verifies the real signature
# with the real verifier, so a broken crypto path fails the build instead of
# shipping.
cp "$INDEX_DIR/index.json" "$TEST_RES/index.json"
cp "$INDEX_DIR/index.json.sig" "$TEST_RES/index.json.sig"

for key in "$INDEX_DIR"/keys/*.pub.pem; do
  cp "$key" "$ASSETS/$(basename "$key")"
  cp "$key" "$TEST_RES/$(basename "$key")"
done

key_id="$(cd "$INDEX_DIR" && node tools/keys.mjs show 2>/dev/null | tail -1 | sed 's/.*: //')"
apps="$(node -e "console.log(JSON.parse(require('fs').readFileSync('$INDEX_DIR/index.json','utf8')).apps.length)")"
size="$(du -h "$ASSETS/index.json" | cut -f1)"

echo "sincronizado: $apps apps, $size, chave $key_id"
