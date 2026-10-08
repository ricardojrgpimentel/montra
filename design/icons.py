#!/usr/bin/env python3
"""Gera as propostas de ícone da Montra.

Uma só geometria, dois destinos: SVG para pré-visualizar (Chrome headless) e
VectorDrawable para a app. Todos os desenhos cabem no círculo de segurança de
66dp do ícone adaptativo (raio 33 a partir do centro 54,54) — o ícone antigo
não cabia e era cortado pelas máscaras circulares.

Uso:  python3 design/icons.py             # escreve design/icon-preview/concepts/*.svg
      python3 design/icons.py --android d1 # escreve o VectorDrawable de um conceito
      python3 design/icons.py --png d1     # escreve design/montra-icone-loja-512.png (ficha da loja)
"""

import math
import sys
from pathlib import Path

from render import render_svg

ROOT = Path(__file__).resolve().parent.parent
PREVIEW = ROOT / "design" / "icon-preview" / "concepts"
DRAWABLE = ROOT / "app" / "src" / "main" / "res" / "drawable"

GREEN = "#2E6B4F"
VIEWPORT = 108
CENTER = 54.0
SAFE_RADIUS = 33.0


def rounded_rect(x, y, w, h, r, reverse=False):
    """Subcaminho de um retângulo de cantos redondos, em sentido horário.

    reverse=True desenha o mesmo retângulo ao contrário: é assim que se abre um
    buraco num caminho com fillType evenOdd.
    """
    x0, y0, x1, y1 = x, y, x + w, y + h
    r = min(r, w / 2, h / 2)
    # Começa na aresta esquerda, logo abaixo do canto superior esquerdo. Cada
    # arco é um quarto de círculo entre dois pontos vizinhos do canto.
    if not reverse:
        return (
            f"M{x0:.2f},{y0 + r:.2f} "
            f"a{r:.2f},{r:.2f} 0 0 1 {r:.2f},{-r:.2f} "
            f"h{w - 2 * r:.2f} "
            f"a{r:.2f},{r:.2f} 0 0 1 {r:.2f},{r:.2f} "
            f"v{h - 2 * r:.2f} "
            f"a{r:.2f},{r:.2f} 0 0 1 {-r:.2f},{r:.2f} "
            f"h{-(w - 2 * r):.2f} "
            f"a{r:.2f},{r:.2f} 0 0 1 {-r:.2f},{-r:.2f} z"
        )
    return (
        f"M{x0:.2f},{y0 + r:.2f} "
        f"v{h - 2 * r:.2f} "
        f"a{r:.2f},{r:.2f} 0 0 0 {r:.2f},{r:.2f} "
        f"h{w - 2 * r:.2f} "
        f"a{r:.2f},{r:.2f} 0 0 0 {r:.2f},{-r:.2f} "
        f"v{-(h - 2 * r):.2f} "
        f"a{r:.2f},{r:.2f} 0 0 0 {-r:.2f},{-r:.2f} "
        f"h{-(w - 2 * r):.2f} z"
    )


def centered_rect(w, h, y, r):
    """Retângulo centrado no eixo horizontal do ícone."""
    return (CENTER - w / 2, y, w, h, r)


# Cada conceito é uma lista de traços: (caminho, regra_de_preenchimento).
# 'row' centra um conjunto de retângulos iguais com um espaço entre eles.
CONCEPTS = {
    "d1": {
        "nome": "Prateleira: três apps, uma prateleira e a faixa",
        "nota": "Lê-se como uma montra com produtos expostos sobre a faixa do catálogo.",
        "shapes": [
            ("row", {"count": 3, "size": 12, "gap": 4, "y": 33, "r": 3.5}),
            ("rect", centered_rect(40, 5, 51, 2.5)),
            ("rect", centered_rect(52, 8, 62, 4)),
        ],
    },
    "d2": {
        "nome": "Vitrine: uma janela com uma app e a faixa",
        "nota": "A janela é a montra; a app fica lá dentro, a faixa fecha em baixo.",
        "shapes": [
            ("frame", {"outer": centered_rect(44, 44, 32, 13), "stroke": 5}),
            ("rect", centered_rect(14, 14, 42, 4)),
            ("rect", centered_rect(26, 7, 62, 3)),
        ],
    },
    "d3": {
        "nome": "Uma app sobre a faixa",
        "nota": "O mínimo que ainda diz o que a app é: um produto, e a faixa por baixo.",
        "shapes": [
            ("rect", centered_rect(22, 22, 32, 7)),
            ("rect", centered_rect(46, 9, 63, 4.5)),
        ],
    },
    "d4": {
        "nome": "Vitrine vazada e faixa",
        "nota": "A janela é um bloco cheio com a app vazada; a faixa fica solta por baixo.",
        "shapes": [
            ("frame", {"outer": centered_rect(44, 44, 24, 16), "stroke": 16, "hollow": True}),
            ("rect", centered_rect(36, 8, 74, 4)),
        ],
    },
    "d5": {
        "nome": "Três apps e a faixa",
        "nota": "Sem prateleira a mais: três apps e a faixa, dois elementos só.",
        "shapes": [
            ("row", {"count": 3, "size": 13, "gap": 4, "y": 36, "r": 4}),
            ("rect", centered_rect(52, 9, 62, 4.5)),
        ],
    },
    "d6": {
        "nome": "Três apps sobre a faixa",
        "nota": "As apps assentam na faixa: é o catálogo que as segura.",
        "shapes": [
            ("row", {"count": 3, "size": 12, "gap": 5, "y": 46, "r": 4}),
            ("rect", centered_rect(52, 9, 58, 4.5)),
        ],
    },
}


def shape_paths(shape):
    """Devolve (path, fill_type) de uma forma."""
    kind, arg = shape
    if kind == "rect":
        x, y, w, h, r = arg
        return [(rounded_rect(x, y, w, h, r), "nonZero")]
    if kind == "row":
        count, size, gap = arg["count"], arg["size"], arg["gap"]
        total = count * size + (count - 1) * gap
        x = CENTER - total / 2
        out = []
        for i in range(count):
            out.append((rounded_rect(x + i * (size + gap), arg["y"], size, size, arg["r"]), "nonZero"))
        return out
    if kind == "frame":
        x, y, w, h, r = arg["outer"]
        if arg.get("hollow"):
            # Bloco cheio com um retângulo vazado dentro: a app vista pela janela.
            inner = (CENTER - 8, y + 10, 16, 16, 5)
            path = rounded_rect(x, y, w, h, r) + " " + rounded_rect(*inner, reverse=True)
            return [(path, "evenOdd")]
        stroke = arg["stroke"]
        inner = (x + stroke, y + stroke, w - 2 * stroke, h - 2 * stroke, max(r - stroke, 0))
        path = rounded_rect(x, y, w, h, r) + " " + rounded_rect(*inner, reverse=True)
        return [(path, "evenOdd")]
    raise ValueError(kind)


def _rect_outline(x, y, w, h, r, steps=24):
    """Pontos do contorno de um retângulo de cantos redondos."""
    r = min(r, w / 2, h / 2)
    corners = [
        (x + r, y + r, math.pi, 1.5 * math.pi),          # superior esquerdo
        (x + w - r, y + r, 1.5 * math.pi, 2 * math.pi),  # superior direito
        (x + w - r, y + h - r, 0.0, 0.5 * math.pi),      # inferior direito
        (x + r, y + h - r, 0.5 * math.pi, math.pi),      # inferior esquerdo
    ]
    pts = []
    for cx, cy, a0, a1 in corners:
        for i in range(steps + 1):
            a = a0 + (a1 - a0) * i / steps
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


def farthest_point(shape):
    """Distância do ponto mais afastado da forma ao centro do ícone."""
    kind, arg = shape
    boxes = []
    if kind == "rect":
        boxes.append(arg)
    elif kind == "row":
        count, size, gap = arg["count"], arg["size"], arg["gap"]
        total = count * size + (count - 1) * gap
        x0 = CENTER - total / 2
        for i in range(count):
            boxes.append((x0 + i * (size + gap), arg["y"], size, size, arg["r"]))
    elif kind == "frame":
        boxes.append(arg["outer"])
    worst = 0.0
    for box in boxes:
        for px, py in _rect_outline(*box):
            worst = max(worst, math.hypot(px - CENTER, py - CENTER))
    return worst


def art_center(concept):
    """Centro da caixa que a arte ocupa, no viewport de 108."""
    xs, ys = [], []
    for shape in concept["shapes"]:
        for x, y, w, h in _shape_boxes(shape):
            xs += [x, x + w]
            ys += [y, y + h]
    return (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2


def _shape_boxes(shape):
    kind, arg = shape
    if kind == "rect":
        return [(arg[0], arg[1], arg[2], arg[3])]
    if kind == "row":
        count, size, gap = arg["count"], arg["size"], arg["gap"]
        total = count * size + (count - 1) * gap
        x0 = CENTER - total / 2
        return [(x0 + i * (size + gap), arg["y"], size, size) for i in range(count)]
    if kind == "frame":
        x, y, w, h, _ = arg["outer"]
        return [(x, y, w, h)]
    raise ValueError(kind)


def svg(concept, view=(0, 0, 108, 108), size=512):
    """SVG do ícone.

    Com o viewBox completo (108) sai a arte com a sangria do ícone adaptativo,
    que é o que se pré-visualiza. Com o viewBox da área visível (72, centrado na
    arte) sai o ícone para a ficha da loja, que não leva sangria: quem recorta é
    a loja, e o quadrado da loja não leva a subida óptica que o círculo do
    launcher pede.
    """
    vx, vy, vw, vh = view
    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" '
        f'viewBox="{vx} {vy} {vw} {vh}">',
        f'  <rect width="108" height="108" fill="{GREEN}"/>',
    ]
    for shape in concept["shapes"]:
        for path, fill_type in shape_paths(shape):
            rule = ' fill-rule="evenodd"' if fill_type == "evenOdd" else ""
            parts.append(f'  <path fill="#FFFFFF"{rule} d="{path}"/>')
    parts.append("</svg>")
    return "\n".join(parts) + "\n"


def vector_drawable(concept):
    parts = [
        '<?xml version="1.0" encoding="utf-8"?>',
        f'<!-- {concept["nome"]} — {concept["nota"]} -->',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="108dp"',
        '    android:height="108dp"',
        '    android:viewportWidth="108"',
        '    android:viewportHeight="108">',
    ]
    for shape in concept["shapes"]:
        for path, fill_type in shape_paths(shape):
            fill_rule = '\n        android:fillType="evenOdd"' if fill_type == "evenOdd" else ""
            parts.append(
                "    <path\n"
                '        android:fillColor="#FFFFFF"'
                f"{fill_rule}\n"
                f'        android:pathData="{path}" />'
            )
    parts.append("</vector>")
    return "\n".join(parts) + "\n"


def main():
    args = sys.argv[1:]
    if args and args[0] == "--android":
        key = args[1]
        concept = CONCEPTS[key]
        DRAWABLE.mkdir(parents=True, exist_ok=True)
        out = DRAWABLE / "ic_launcher_foreground.xml"
        out.write_text(vector_drawable(concept), encoding="utf-8")
        print(f"{out} <- {concept['nome']} (safe zone: {farthest_point_of(concept):.1f}/33)")
        return

    if args and args[0] == "--png":
        # O ícone de 512x512 que a ficha da loja pede: só a área visível (72dp),
        # sem a sangria dos 108dp, senão a arte sai pequena numa loja que não
        # aplica a máscara do launcher.
        key = args[1]
        cx, cy = art_center(CONCEPTS[key])
        PREVIEW.mkdir(parents=True, exist_ok=True)
        svg_path = PREVIEW / f"{key}-loja.svg"
        svg_path.write_text(
            svg(CONCEPTS[key], view=(cx - 36, cy - 36, 72, 72), size=512), encoding="utf-8"
        )
        out = ROOT / "design" / "montra-icone-loja-512.png"
        render_svg(svg_path, out, 512, 512, scale=1, transparent=False)
        print(f"{out} <- {CONCEPTS[key]['nome']} (512x512, área visível)")
        return

    PREVIEW.mkdir(parents=True, exist_ok=True)
    for key, concept in CONCEPTS.items():
        (PREVIEW / f"{key}.svg").write_text(svg(concept), encoding="utf-8")
        print(f"{key}: {concept['nome']} — safe zone {farthest_point_of(concept):.1f}/33")


def farthest_point_of(concept):
    return max(farthest_point(s) for s in concept["shapes"])


if __name__ == "__main__":
    main()
