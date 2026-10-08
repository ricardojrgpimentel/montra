#!/usr/bin/env python3
"""Confirma que o VectorDrawable da app desenha exatamente o conceito aprovado.

Lê o android:pathData do ficheiro instalado em res/drawable, reconstrói um SVG
com os mesmos traços e compara-o, pixel a pixel, com a pré-visualização do
conceito. Se os dois não baterem certo, o ícone que se vê no telemóvel não é o
que foi aprovado.

Uso: python3 design/verify-drawable.py d1
"""

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DRAWABLE = ROOT / "app" / "src" / "main" / "res" / "drawable" / "ic_launcher_foreground.xml"
PREVIEW = ROOT / "design" / "icon-preview" / "concepts"
GREEN = "#2E6B4F"


def paths_from_drawable(text):
    out = []
    for block in re.findall(r"<path\b(.*?)/>", text, re.S):
        data = re.search(r'android:pathData="([^"]+)"', block)
        fill = "#FFFFFF" if 'android:fillColor="#FFFFFF"' in block else "#000000"
        even_odd = 'android:fillType="evenOdd"' in block
        out.append((data.group(1), fill, even_odd))
    return out


def svg_from_paths(paths):
    lines = [
        '<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 108 108">',
        f'  <rect width="108" height="108" fill="{GREEN}"/>',
    ]
    for data, fill, even_odd in paths:
        rule = ' fill-rule="evenodd"' if even_odd else ""
        lines.append(f'  <path fill="{fill}"{rule} d="{data}"/>')
    lines.append("</svg>")
    return "\n".join(lines) + "\n"


def main():
    concept = sys.argv[1] if len(sys.argv) > 1 else "d1"
    text = DRAWABLE.read_text(encoding="utf-8")
    paths = paths_from_drawable(text)
    if not paths:
        sys.exit("nenhum <path> encontrado no VectorDrawable")

    round_trip = PREVIEW / f"_roundtrip-{concept}.svg"
    round_trip.write_text(svg_from_paths(paths), encoding="utf-8")
    print(f"{len(paths)} traços lidos de {DRAWABLE.name}")

    shell = Path.home() / (
        "Library/Caches/ms-playwright/chromium_headless_shell-1243/"
        "chrome-headless-shell-mac-arm64/chrome-headless-shell"
    )
    for name, svg in (("roundtrip", round_trip), ("concept", PREVIEW / f"{concept}.svg")):
        subprocess.run(
            [
                str(shell), "--headless", "--disable-gpu", "--no-sandbox", "--hide-scrollbars",
                "--force-device-scale-factor=1", f"--screenshot={PREVIEW / f'_{name}.png'}",
                "--window-size=540,540", f"file://{svg}",
            ],
            check=True,
            capture_output=True,
        )

    diff = subprocess.run(
        [
            "magick", "compare", "-metric", "AE",
            str(PREVIEW / "_roundtrip.png"), str(PREVIEW / "_concept.png"), "null:",
        ],
        capture_output=True,
        text=True,
    )
    # O ImageMagick escreve "0 (0)": o primeiro número são os píxeis diferentes.
    pixels = (diff.stderr or "0").strip().split()[0]
    print(f"píxeis diferentes entre o drawable e o conceito: {pixels}")
    for temp in (round_trip, PREVIEW / "_roundtrip.png", PREVIEW / "_concept.png"):
        temp.unlink(missing_ok=True)
    if float(pixels) != 0:
        sys.exit("o drawable instalado não corresponde ao conceito aprovado")
    print("ok: o drawable desenha exatamente o conceito")


if __name__ == "__main__":
    main()
