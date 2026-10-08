#!/usr/bin/env python3
"""Exporta a Direção 1 aprovada para VectorDrawable, sem dependências externas.

O SVG do Stitch é a fonte: preserva módulos, detalhes, prateleira e gradientes.
As sombras e guias de construção ficam na apresentação, fora do ícone Android.
Uso: python3 design/stitch.py
"""

import xml.etree.ElementTree as ET
from pathlib import Path

from icons import rounded_rect

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "design/stitch-concepts/stitch-d1-storefront.svg"
OUT = ROOT / "app/src/main/res/drawable"


def drawable_paths():
    root = ET.parse(SOURCE).getroot()
    gradients = {e.attrib["id"]: e for e in root.iter()
                 if e.tag.endswith("linearGradient")}
    paths = []
    for e in root.iter():
        a = e.attrib
        tag = e.tag.rsplit("}", 1)[-1]
        # Only filled artwork and the solid foundation strokes; construction
        # circles/axes and SVG filters are presentation aids.
        if "stroke-dasharray" in a:
            continue
        if tag == "rect":
            data = rounded_rect(float(a.get("x", 0)), float(a.get("y", 0)),
                                float(a["width"]), float(a["height"]),
                                float(a.get("rx", 0)))
        elif tag == "circle" and "fill" in a:
            x, y, r = (float(a[k]) for k in ("cx", "cy", "r"))
            data = f"M{x-r},{y} a{r},{r} 0 1 0 {2*r},0 a{r},{r} 0 1 0 {-2*r},0 z"
        elif tag == "path":
            data = a["d"]
        else:
            continue
        attrs = [f'android:pathData="{data}"']
        fill = a.get("fill", "#00000000")
        gradient = None
        if fill.startswith("url(#"):
            gradient = gradients[fill[5:-1]]
        else:
            attrs.append(f'android:fillColor="{fill}"')
        for svg, android in (("fill-opacity", "fillAlpha"), ("stroke", "strokeColor"),
                             ("stroke-width", "strokeWidth"), ("stroke-opacity", "strokeAlpha"),
                             ("stroke-linecap", "strokeLineCap")):
            if svg in a:
                attrs.append(f'android:{android}="{a[svg]}"')
        path = "    <path " + "\n        ".join(attrs)
        if gradient is None:
            path += " />"
        else:
            # The source gradients run across the plate/shelf bounding boxes.
            x, y = float(a.get("x", 0)), float(a.get("y", 0))
            w, h = float(a["width"]), float(a["height"])
            coords = {k: (x if k.endswith("X") else y) +
                      (w if k.endswith("X") else h) *
                      float(gradient.attrib[s].rstrip("%")) / 100
                      for k, s in (("startX", "x1"), ("startY", "y1"),
                                   ("endX", "x2"), ("endY", "y2"))}
            path += '>\n        <aapt:attr name="android:fillColor">\n'
            path += '            <gradient android:type="linear" ' + " ".join(
                f'android:{k}="{v}"' for k, v in coords.items()) + '>\n'
            for stop in gradient:
                sa = stop.attrib
                alpha = round(float(sa.get("stop-opacity", 1)) * 255)
                color = f'#{alpha:02X}{sa["stop-color"][1:]}'
                offset = float(sa["offset"].rstrip("%")) / 100
                path += f'                <item android:color="{color}" android:offset="{offset}" />\n'
            path += '            </gradient>\n        </aapt:attr>\n    </path>'
        paths.append(path)
    return paths


def vector(paths, size, viewport, group=None):
    body = "\n".join(paths)
    if group:
        body = f'    <group {group}>\n{body}\n    </group>'
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<!-- Gerado por design/stitch.py a partir da Direção 1 do Stitch. -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    xmlns:aapt="http://schemas.android.com/aapt"\n'
            f'    android:width="{size}dp" android:height="{size}dp"\n'
            f'    android:viewportWidth="{viewport}" android:viewportHeight="{viewport}">\n'
            f'{body}\n</vector>\n')


def outputs():
    paths = drawable_paths()
    # The approved 512px canvas occupies the visible 72dp of a 108dp adaptive
    # icon. Background bleeds to the edges; artwork remains inside the safe zone.
    group = ('android:translateX="18" android:translateY="18" '
             'android:scaleX="0.140625" android:scaleY="0.140625"')
    background = paths[0].replace(rounded_rect(0, 0, 512, 512, 112),
                                 "M0,0 H512 V512 H0 Z")
    # The monochrome silhouette excludes the inset windows/indicators.
    mono = []
    for i in (1, 3, 6, 9, 11, 12, 13, 14):
        element = ET.fromstring('<root xmlns:android="http://schemas.android.com/apk/res/android" '
                                'xmlns:aapt="http://schemas.android.com/aapt">' + paths[i] + '</root>')[0]
        data = element.attrib['{http://schemas.android.com/apk/res/android}pathData']
        stroke = element.attrib.get('{http://schemas.android.com/apk/res/android}strokeWidth')
        paint = ('android:fillColor="#00000000" android:strokeColor="#FFFFFF" '
                 f'android:strokeWidth="{stroke}" android:strokeLineCap="round"') if stroke else 'android:fillColor="#FFFFFF"'
        mono.append(f'    <path android:pathData="{data}" {paint} />')
    return {
        "montra_mark.xml": vector(paths, 48, 512),
        "ic_launcher_foreground.xml": vector(paths[1:], 108, 108, group),
        "ic_launcher_background.xml": vector([background], 108, 512),
        "ic_launcher_monochrome.xml": vector(mono, 108, 108, group),
    }


if __name__ == "__main__":
    for name, content in outputs().items():
        (OUT / name).write_text(content, encoding="utf-8")
        print(f"{name} ← {SOURCE.name}")
