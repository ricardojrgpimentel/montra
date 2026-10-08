#!/usr/bin/env python3
"""Gera o wordmark da Montra — a marca e o nome, em vetor, sem depender de fontes.

O nome é convertido em contornos (não é texto com font-family): um logo que muda
de forma conforme a fonte que existir na máquina não é um logo. Os contornos vêm
do Roboto Flex variável, instanciado no peso 500, e ficam no SVG — o ficheiro
abre igual em qualquer lado, sem trazer a fonte atrás.

A marca é a mesma geometria do ícone (design/icons.py), dentro de um quadrado de
cantos redondos: o logo e o ícone não podem divergir.

Precisa de: fontTools (`pip install fontTools`) e chrome-headless-shell para os
PNG. Uso:

    python3 design/wordmark.py                  # escreve design/wordmark/*.svg|*.png
    python3 design/wordmark.py --loja           # e o gráfico de destaque da loja
    python3 design/wordmark.py --fonte CAMINHO  # outro TTF para o nome
"""

import argparse
import subprocess
import sys
from pathlib import Path

import icons  # a mesma geometria da marca que o ícone usa
from render import render_svg

# O fontTools só é preciso para gerar o logo, não para compilar a app: aceita-se
# instalado no sistema ou em design/.deps (pip install --target design/.deps).
sys.path.append(str(Path(__file__).resolve().parent / ".deps"))
try:
    from fontTools.pens.svgPathPen import SVGPathPen
    from fontTools.ttLib import TTFont
    from fontTools.varLib import instancer
except ImportError:  # pragma: no cover - mensagem para quem clonar o repositório
    sys.exit("falta o fontTools: python3 -m pip install --target design/.deps fontTools")

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "design" / "wordmark"
STORE = ROOT / "design" / "loja"

GREEN = icons.GREEN
INK = "#101410"
WHITE = "#FFFFFF"
SURFACE = "#FBFDF8"   # a superfície da casa, a mesma que a app usa por baixo do conteúdo

WIDE, WIDE_H = 1024, 500   # medidas que a ficha da loja pede para o destaque
LOGO_SHARE = 0.56          # quanto da largura o logo ocupa no gráfico

WORD = "Montra"
WEIGHT = 500
PLATE = 96.0          # lado do quadrado da marca
GAP = 34.0            # espaço entre a marca e o nome
PAD = 8.0             # respiro à volta de tudo
TRACKING = -0.012     # aperto entre letras, em fracção do tamanho do nome
CAP_RATIO = 0.48      # altura das maiúsculas do nome, em fracção do lado da marca

FONTES = [
    Path(
        "/Applications/Android Studio.app/Contents/plugins/design-tools/resources/"
        "layoutlib/data/fonts/RobotoFlex-Regular.ttf"
    ),
    Path(
        "/Applications/Android Studio.app/Contents/plugins/design-tools/resources/"
        "layoutlib/data/fonts/Roboto-Regular.ttf"
    ),
]


def load_font(path=None, weight=WEIGHT):
    """Abre a fonte do nome; instancia a variável no peso pedido."""
    candidates = [Path(path)] if path else FONTES
    for candidate in candidates:
        if not candidate.exists():
            continue
        font = TTFont(candidate, fontNumber=0)
        if "fvar" in font:
            font = instancer.instantiateVariableFont(font, {"wght": weight}, inplace=False)
        return font, candidate
    sys.exit("nenhuma fonte encontrada:\n  " + "\n  ".join(str(c) for c in candidates))


def word_outline(font, text, size):
    """Contornos do nome, em coordenadas de glifo, e a largura total em px."""
    glyph_set = font.getGlyphSet()
    cmap = font.getBestCmap()
    hmtx = font["hmtx"]
    scale = size / font["head"].unitsPerEm
    tracking = TRACKING * size

    glyphs = []
    x = 0.0
    for char in text:
        name = cmap.get(ord(char))
        if name is None:
            sys.exit(f"a fonte não tem o glifo de {char!r}")
        pen = SVGPathPen(glyph_set)
        glyph_set[name].draw(pen)
        glyphs.append((x, pen.getCommands()))
        x += hmtx[name][0] * scale + tracking
    return glyphs, x - tracking, scale  # o último tracking não é espaço a desenhar


def mark_group(size, art=WHITE):
    """A marca: quadrado de cantos redondos com a arte do ícone centrada.

    O quadrado do logo vale pela área *visível* do ícone adaptativo (72dp dos
    108dp), não pelo quadrado todo: é isso que faz a arte ler-se do mesmo
    tamanho no logo e no ícone. Escalar por 108 deixava a marca acanhada ao pé
    do nome.
    """
    art_scale = size / 72.0
    boxes = [b for shape in icons.CONCEPTS["d1"]["shapes"] for b in shapes_boxes(shape)]
    xs = [b[0] for b in boxes] + [b[0] + b[2] for b in boxes]
    ys = [b[1] for b in boxes] + [b[1] + b[3] for b in boxes]
    tx = size / 2 - (min(xs) + max(xs)) / 2 * art_scale
    ty = size / 2 - (min(ys) + max(ys)) / 2 * art_scale

    parts = [
        f'  <rect width="{size:.2f}" height="{size:.2f}" rx="{size * 0.225:.2f}" fill="{GREEN}"/>',
        f'  <g transform="translate({tx:.3f},{ty:.3f}) scale({art_scale:.5f})">',
    ]
    for shape in icons.CONCEPTS["d1"]["shapes"]:
        for path, fill_type in icons.shape_paths(shape):
            rule = ' fill-rule="evenodd"' if fill_type == "evenOdd" else ""
            parts.append(f'    <path fill="{art}"{rule} d="{path}"/>')
    parts.append("  </g>")
    return "\n".join(parts)


def shapes_boxes(shape):
    """Caixas (x, y, w, h) que compõem uma forma — só para medir a arte."""
    kind, arg = shape
    if kind == "rect":
        return [(arg[0], arg[1], arg[2], arg[3])]
    if kind == "row":
        count, size, gap = arg["count"], arg["size"], arg["gap"]
        total = count * size + (count - 1) * gap
        x0 = icons.CENTER - total / 2
        return [(x0 + i * (size + gap), arg["y"], size, size) for i in range(count)]
    if kind == "frame":
        x, y, w, h, _ = arg["outer"]
        return [(x, y, w, h)]
    raise ValueError(kind)


def lockup_body(word_color, fonte=None):
    """Traços do logo e as dimensões da caixa que ele ocupa.

    Separado do SVG que o embrulha para o mesmo logo poder entrar dentro de outra
    composição — o gráfico da loja, por exemplo — sem uma segunda geometria.
    """
    font, fonte = load_font(fonte)
    cap = font["OS/2"].sCapHeight / font["head"].unitsPerEm
    size = PLATE * CAP_RATIO / cap           # o nome fica com a altura pedida
    glyphs, word_width, scale = word_outline(font, WORD, size)

    width = PAD + PLATE + GAP + word_width + PAD
    height = PAD * 2 + PLATE
    baseline = PAD + PLATE / 2 + PLATE * CAP_RATIO / 2

    lines = [
        f'  <g transform="translate({PAD:.2f},{PAD:.2f})">',
        mark_group(PLATE),
        "  </g>",
        f'  <g fill="{word_color}" transform="translate({PAD + PLATE + GAP:.2f},{baseline:.2f})">',
    ]
    for x, commands in glyphs:
        # Os contornos vêm em unidades da fonte, com o y para cima: o SVG tem o
        # y para baixo, daí o scale positivo em x e negativo em y.
        lines.append(
            f'    <path transform="translate({x:.2f},0) scale({scale:.5f},{-scale:.5f})" '
            f'd="{commands}"/>'
        )
    lines.append("  </g>")
    return lines, width, height, fonte


def lockup(word_color, fonte=None):
    """SVG do logo (marca + nome), sozinho."""
    lines, width, height, fonte = lockup_body(word_color, fonte)
    svg = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width:.2f}" height="{height:.2f}" '
        f'viewBox="0 0 {width:.2f} {height:.2f}">',
        "  <title>Montra</title>",
        *lines,
        "</svg>",
    ]
    return "\n".join(svg) + "\n", width, height, fonte


def store_graphic(fonte=None, width=WIDE, height=WIDE_H, fill=SURFACE, word_color=INK):
    """Gráfico de destaque da loja: o logo sobre a superfície da casa.

    Sem slogan inventado e sem uma terceira versão da marca: é o mesmo logo, na
    cor da superfície, que é o que a app já usa por baixo do conteúdo.
    """
    lines, logo_w, logo_h, fonte = lockup_body(word_color, fonte)
    scale = width * LOGO_SHARE / logo_w
    tx = (width - logo_w * scale) / 2
    ty = (height - logo_h * scale) / 2
    svg = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" '
        f'viewBox="0 0 {width} {height}">',
        "  <title>Montra</title>",
        f'  <rect width="{width}" height="{height}" fill="{fill}"/>',
        f'  <g transform="translate({tx:.2f},{ty:.2f}) scale({scale:.5f})">',
        *lines,
        "  </g>",
        "</svg>",
    ]
    return "\n".join(svg) + "\n", fonte


def render(svg_path, png_path, width, height, scale=2):
    """Exporta o SVG para PNG com fundo transparente."""
    return render_svg(svg_path, png_path, width, height, scale=scale, transparent=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--fonte", default=None, help="TTF alternativo para o nome")
    parser.add_argument("--escala", type=int, default=2, help="escala dos PNG (predefinição 2)")
    parser.add_argument("--loja", action="store_true", help="escreve o gráfico de destaque da loja")
    args = parser.parse_args()

    OUT.mkdir(parents=True, exist_ok=True)
    for name, color in (
        ("montra-wordmark", INK),
        ("montra-wordmark-invertido", WHITE),
    ):
        svg, width, height, fonte = lockup(color, args.fonte)
        svg_path = OUT / f"{name}.svg"
        svg_path.write_text(svg, encoding="utf-8")
        render(svg_path, OUT / f"{name}@{args.escala}x.png", width, height, args.escala)
        print(f"{svg_path.name}: {width:.0f}x{height:.0f} — fonte {fonte.name}")

    if args.loja:
        STORE.mkdir(parents=True, exist_ok=True)
        svg, fonte = store_graphic(args.fonte)
        svg_path = STORE / "grafico-destaque.svg"
        svg_path.write_text(svg, encoding="utf-8")
        render_svg(
            svg_path, STORE / "grafico-destaque-1024x500.png",
            WIDE, WIDE_H, scale=1, transparent=False,
        )
        print(f"{svg_path.name}: {WIDE}x{WIDE_H} — fonte {fonte.name}")


if __name__ == "__main__":
    main()
