# Conceitos de Identidade Visual e Ícone (Stitch MCP)

Exploração de propostas de identidade visual e ícone adaptativo Android para a Montra, geradas através do servidor MCP do Stitch (Google Stitch).

Projeto Stitch: **`Montra Logo & App Icon Design`** (ID: `7777111789677733119`)

---

## Conceito Escolhido: Direção 1 (Vitrine & Prateleira Minimalista)

* **Ícone Principal**:
  * `stitch-d1-storefront.svg`: Arte vetorial completa (512×512) com grelha de segurança central de 66dp, base squircle em verde floresta (`#2E6B4F`), três módulos de apps curadas com janela de visualização e indicadores, prateleira com linha de horizonte e escoras arquiteturais.
  * `stitch-d1-storefront-512.png`: Renderização raster a 512×512 para ficha da loja e mockups.
* **Logótipo / Wordmark**:
  * `stitch-d1-wordmark-showcase.svg` e `stitch-d1-wordmark-showcase.png`: Apresentação oficial gerada pelo Stitch com as variantes para fundos claros (`#F7FAF3` / `#101410`) e escuros (`#141814` / `#FFFFFF`).
  * `montra-wordmark-stitch.svg` e `montra-wordmark-stitch@2x.png`: Versão horizontal transparente para integração web/documentação.

## Integração Android

`python3 design/stitch.py` converte `stitch-d1-storefront.svg` em
`montra_mark.xml`, `ic_launcher_foreground.xml`, `ic_launcher_background.xml` e
`ic_launcher_monochrome.xml`. A versão Android conserva a geometria dos módulos,
os detalhes e os gradientes; remove as sombras e as guias de construção.
O fundo tem sangria completa e o símbolo ocupa a área visível de 72dp no viewport
adaptativo de 108dp. A versão monocromática usa a silhueta dos módulos e prateleira.

A marca aparece no cabeçalho Apps e no Sobre. O nome usa texto nativo e adapta-se
ao tema claro/escuro. O README usa o wordmark PNG aprovado.
`python3 design/verify-drawable.py stitch-d1` verifica os quatro recursos gerados.

---

## Conceitos Alternativos Explorados

* **Direção 2 — Monograma 'M' Arquitetural**:
  * `stitch-d2-monogram.svg` / `stitch-d2-monogram-512.png`: Letra 'M' desenhada como arcos de vitrine gémeos assentes na prateleira com vitral central.
* **Direção 3 — Confiança & Transparência Criptográfica**:
  * `stitch-d3-trust.svg` / `stitch-d3-trust-512.png`: Selo hexagonal de integridade matemática envolvendo a vitrine com o visto de assinatura digital.
