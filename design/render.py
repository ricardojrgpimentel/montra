#!/usr/bin/env python3
"""Renderiza SVG para PNG com o chrome-headless-shell.

Partilhado pelo ícone e pelo wordmark: os dois precisam do mesmo motor (o
renderizador SVG interno do ImageMagick erra os arcos e comia um círculo inteiro
do ícone antigo, o que dava pré-visualizações mentirosas).

Precisa do chrome-headless-shell do Playwright; CHROME_SHELL aponta para outro.
"""

import os
import subprocess
import sys
from pathlib import Path

DEFAULT_SHELL = (
    Path.home()
    / "Library/Caches/ms-playwright/chromium_headless_shell-1243"
    / "chrome-headless-shell-mac-arm64/chrome-headless-shell"
)


def shell_path():
    return Path(os.environ.get("CHROME_SHELL", DEFAULT_SHELL))


def render_svg(svg_path, png_path, width, height, scale=1, transparent=True):
    """Escreve png_path a partir do SVG. Devolve False se não houver motor."""
    svg_path, png_path = Path(svg_path).resolve(), Path(png_path)
    shell = shell_path()
    if not shell.exists():
        print(f"sem chrome-headless-shell: {png_path.name} não foi exportado", file=sys.stderr)
        return False
    background = "00000000" if transparent else "FFFFFFFF"
    subprocess.run(
        [
            str(shell), "--headless", "--disable-gpu", "--no-sandbox", "--hide-scrollbars",
            # A janela é em px de CSS; o device-scale-factor é que multiplica a
            # imagem final (janela de 401x112 com escala 2 dá um PNG de 802x224).
            f"--force-device-scale-factor={scale}",
            f"--default-background-color={background}",
            f"--screenshot={png_path.resolve()}",
            f"--window-size={round(width)},{round(height)}",
            f"file://{svg_path}",
        ],
        check=True,
        capture_output=True,
    )
    return True
