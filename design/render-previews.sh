#!/usr/bin/env bash
# Pré-visualiza os ícones como o launcher os mostra e monta as folhas de
# comparação. Sem argumentos, faz todos os conceitos.
#
#   ./design/render-previews.sh          # todos
#   ./design/render-previews.sh d1 d6    # só alguns
#
# Precisa de ImageMagick (`magick`) e do chrome-headless-shell do Playwright
# (ou de CHROME_SHELL a apontar para outro binário headless).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/design/icon-preview/concepts"
FONT="${FONT:-/System/Library/Fonts/Helvetica.ttc}"
SHELL_BIN="${CHROME_SHELL:-$HOME/Library/Caches/ms-playwright/chromium_headless_shell-1243/chrome-headless-shell-mac-arm64/chrome-headless-shell}"

concepts=("$@")
if [ ${#concepts[@]} -eq 0 ]; then
  concepts=(cur d1 d2 d3 d4 d5 d6)
fi

cd "$OUT"
for f in "${concepts[@]}"; do
  [ -f "$f.svg" ] || { echo "sem $f.svg — corre primeiro: python3 design/icons.py" >&2; exit 1; }
  "$SHELL_BIN" --headless --disable-gpu --no-sandbox --hide-scrollbars \
    --force-device-scale-factor=1 --screenshot="$OUT/$f-540.png" --window-size=540,540 \
    "file://$OUT/$f.svg" >/dev/null 2>&1
  # 108dp -> 540px, logo 72dp visíveis -> 360px centrados.
  magick "$f-540.png" -crop 360x360+90+90 +repage -resize 512x512 -alpha set \
    \( -size 512x512 xc:none -fill white -draw "circle 256,256 256,0" \) \
    -compose DstIn -composite "masked-$f-512.png"
  magick "masked-$f-512.png" -resize 48x48 "small-$f-48.png"
  rm -f "$f-540.png"
done

# Etiqueta uma imagem: $1 ficheiro, $2 tamanho, $3 texto, $4 destino.
label() {
  magick "$1" -resize "$2" -background "#f2f2f2" -alpha remove -alpha off \
    -gravity North -splice 0x60 -font "$FONT" -pointsize 34 -fill "#111111" \
    -annotate +0+14 "$3" "$4"
}

# Antes e agora: a máscara circular a mostrar o corte, e a 48dp.
label "masked-cur-512.png" 420x420 "Antes" _b1.png
label "masked-d1-512.png" 420x420 "Agora" _b2.png
label "small-cur-48.png" 176x176 "48dp" _b3.png
label "small-d1-48.png" 176x176 "48dp" _b4.png
magick _b1.png _b2.png +append _r1.png
magick _b3.png _b4.png +append _r2.png
magick _r1.png _r2.png -append -bordercolor "#f2f2f2" -border 16 antes-depois.png

# As alternativas que ficaram por escolher.
label "masked-d1-512.png" 380x380 "d1 prateleira" _c1.png
label "masked-d3-512.png" 380x380 "d3 uma app" _c2.png
label "masked-d5-512.png" 380x380 "d5 três apps" _c3.png
label "masked-d6-512.png" 380x380 "d6 sobre a faixa" _c4.png
label "small-d1-48.png" 176x176 "48dp" _c5.png
label "small-d3-48.png" 176x176 "48dp" _c6.png
label "small-d5-48.png" 176x176 "48dp" _c7.png
label "small-d6-48.png" 176x176 "48dp" _c8.png
magick _c1.png _c2.png _c3.png _c4.png +append _r3.png
magick _c5.png _c6.png _c7.png _c8.png +append _r4.png
magick _r3.png _r4.png -append -bordercolor "#f2f2f2" -border 16 alternativas.png

rm -f _b*.png _c*.png _r*.png
echo "pré-visualizações em $OUT"
