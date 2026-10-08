#!/usr/bin/env python3
"""Confirma que o VectorDrawable instalado é o conceito aprovado.

Duas verificações, e a primeira não precisa de browser nenhum:

1. O ficheiro em res/drawable é comparado, byte a byte, com o que o gerador
   produz para cada conceito. É esta que corre em CI: se alguém editar o XML à
   mão, ou mudar a geometria sem regerar, falha e diz qual é o conceito que
   corresponde ao ficheiro.
2. O desenho é comparado, píxel a píxel, com a pré-visualização do conceito. Só
   corre localmente, quando há chrome-headless-shell e ImageMagick; sem eles
   avisa e não falha, porque em CI o que interessa é a primeira.

Uso: python3 design/verify-drawable.py        # identifica o conceito sozinho
     python3 design/verify-drawable.py d1     # e exige que seja o d1
"""

import shutil
import subprocess
import sys
from pathlib import Path

import icons
import render

ROOT = Path(__file__).resolve().parent.parent
DRAWABLE = ROOT / "app" / "src" / "main" / "res" / "drawable" / "ic_launcher_foreground.xml"
PREVIEW = ROOT / "design" / "icon-preview" / "concepts"


def identify(text):
    """Conceito cujo desenho é exatamente este ficheiro, ou None."""
    for key, concept in icons.CONCEPTS.items():
        if icons.vector_drawable(concept) == text:
            return key
    return None


def pixel_diff(concept):
    """Píxeis diferentes entre o drawable instalado e o conceito, ou None."""
    if not render.shell_path().exists() or shutil.which("magick") is None:
        return None
    PREVIEW.mkdir(parents=True, exist_ok=True)
    concept_svg = PREVIEW / f"{concept}.svg"
    if not concept_svg.exists():
        concept_svg.write_text(icons.svg(icons.CONCEPTS[concept]), encoding="utf-8")

    round_trip = PREVIEW / f"_{concept}-drawable.svg"
    round_trip.write_text(
        render_svg_from_drawable(DRAWABLE.read_text(encoding="utf-8")), encoding="utf-8"
    )
    shots = {}
    for name, svg in (("roundtrip", round_trip), ("concept", concept_svg)):
        shots[name] = PREVIEW / f"_{name}.png"
        # Os dois SVG têm 512px de lado: com molduras diferentes, a comparação
        # media a diferença entre molduras em vez da diferença entre desenhos.
        render.render_svg(svg, shots[name], 512, 512, scale=1, transparent=False)

    diff = subprocess.run(
        ["magick", "compare", "-metric", "AE", str(shots["roundtrip"]), str(shots["concept"]), "null:"],
        capture_output=True,
        text=True,
    )
    round_trip.unlink(missing_ok=True)
    for shot in shots.values():
        shot.unlink(missing_ok=True)
    # O ImageMagick escreve "0 (0)": o primeiro número são os pixels diferentes.
    return float((diff.stderr or "0").strip().split()[0])


def render_svg_from_drawable(text):
    """SVG com os mesmos tracos do VectorDrawable, para comparar desenhos."""
    lines = [
        '<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 108 108">',
        f'  <rect width="108" height="108" fill="{icons.GREEN}"/>',
    ]
    for block in text.split("<path")[1:]:
        data = block.split('android:pathData="')[1].split('"')[0]
        rule = ' fill-rule="evenodd"' if 'android:fillType="evenOdd"' in block else ""
        lines.append(f'  <path fill="#FFFFFF"{rule} d="{data}"/>')
    lines.append("</svg>")
    return "\n".join(lines) + "\n"


def main():
    esperado = sys.argv[1] if len(sys.argv) > 1 else None
    text = DRAWABLE.read_text(encoding="utf-8")

    key = identify(text)
    if key is None:
        sys.exit(
            f"{DRAWABLE.name} não corresponde a nenhum conceito de design/icons.py.\n"
            "Ou foi editado à mão, ou a geometria mudou sem regerar o ficheiro:\n"
            "  python3 design/icons.py --android <conceito>"
        )
    if esperado and esperado != key:
        sys.exit(f"o drawable instalado é o {key}, mas o esperado era o {esperado}")

    conceito = icons.CONCEPTS[key]
    tracos = text.count("<path")
    print(
        f"{DRAWABLE.name}: {key} ({conceito['nome']}), {tracos} tracos, "
        f"safe zone {icons.farthest_point_of(conceito):.1f}/33"
    )

    pixels = pixel_diff(key)
    if pixels is None:
        print("sem chrome-headless-shell ou ImageMagick: comparação de pixels saltada")
    elif pixels != 0:
        sys.exit(f"o drawable instalado desenha {pixels:.0f} pixels diferentes do conceito {key}")
    else:
        print(f"ok: o drawable desenha exatamente o conceito {key} (0 pixels diferentes)")


if __name__ == "__main__":
    main()
