# Montra — sistema de design

Este ficheiro é o contrato visual da app. Serve para duas coisas: decidir rápido
quando há dúvida, e recusar contribuições que introduzam um valor fora do sistema
sem dizer porquê. Se algo aqui não chega, muda-se aqui primeiro e no código depois.

Foi escrito a seguir a ler as regras do [Impeccable](https://impeccable.style/slop),
um catálogo de hábitos que fazem uma interface parecer gerada por máquina. As
regras que mais nos dizem respeito estão anotadas abaixo como **evitar**.

## Princípios

1. **A informação útil vem primeiro.** Numa loja, isso é: o que é a app, se corre
   aqui, se está instalada, e quanto pesa. Tudo o resto é secundário.
2. **Uma superfície, um nível.** Sem cartões dentro de cartões. Agrupa com espaço,
   tipografia e separadores, não com mais contentores.
3. **A cor comunica estado.** Verde é ação, vermelho é problema, amarelo é atenção.
   Não há cor decorativa. *Evitar: risco lateral colorido em cartões, brilhos,
   gradientes por decoração.*
4. **O movimento explica, não enfeita.** Um progresso mexe-se porque há progresso.
   Nada pulsa para chamar atenção. *Evitar: ponto de estado pulsante, animações em
   hover, easing com bounce.*
5. **Texto a tamanho de ser lido.** Metadados a 12sp, corpo a 14sp. Nunca abaixo de
   11sp, e nunca texto de controlo abaixo de 12sp. *Evitar: texto de interface
   minúsculo.*

## Paleta

Verde porque é a cor do projeto, não porque está na moda. Nada de roxo nem de
ciano sobre fundo escuro. *Evitar: paleta "AI" (gradientes roxos, ciano brilhante),
bege por omissão.*

| Papel | Claro | Escuro |
| --- | --- | --- |
| Primária (ação) | `#2E6B4F` | `#99D5B2` |
| Sobre primária | `#FFFFFF` | `#003920` |
| Contentor primário | `#B4F1CE` | `#12512F` |
| Superfície | `#FBFDF8` | `#101410` |
| Superfície (linha de lista) | `#F1F5EF` | `#1A201A` |
| Erro | `#BA1A1A` | `#FFB4AB` |

**Não usamos cor dinâmica do sistema (Material You).** Uma loja tem identidade: o
verde é a Montra, não o papel de parede de quem instalou. É uma decisão, não um
esquecimento.

## Tipografia

Uma família (a do sistema, Roboto), variando tamanho, peso e espaço. *Evitar: uma
fonte decorativa, itálico em serifas para parecer editorial, cartões com ícone em
caixa acima do título.*

| Uso | Estilo | Tamanho |
| --- | --- | --- |
| Nome da app (linha) | `titleMedium` SemiBold | 16sp |
| Resumo | `bodyMedium` | 14sp |
| Título de secção | `titleSmall` SemiBold | 14sp |
| Metadados (versão, tamanho) | `labelMedium` | 12sp |
| Distintivos | `labelMedium` | 12sp |
| Hash e certificado | `bodySmall` monoespaçado | 12sp |

Regra de espaço dos títulos: **mais espaço acima do que abaixo**. Um título
pertence ao que vem a seguir, não ao que veio antes.

## Espaço

Escala de 4: `4 · 8 · 12 · 16 · 24 · 32`. Não se inventam valores.

- `4` — entre linhas do mesmo par (etiqueta/valor).
- `8` — entre título e resumo de uma app.
- `12` — entre o ícone e o texto de uma linha.
- `16` — entre linhas de lista.
- `24` — entre secções. Nunca igual ao espaço entre linhas, senão tudo pertence a
  tudo. *Evitar: espaçamento monótono.*

## Formas

- Linhas de lista e blocos: `16dp` de raio.
- Ícones de app: `28%` (o mesmo raio relativo em qualquer tamanho).
- Distintivos: `8dp`.
- Botões: `pill` (predefinição do Material 3).

*Evitar: raios exagerados que espremem o conteúdo, e a combinação de contorno fino
com sombra larga — escolhe-se a borda **ou** a sombra. Aqui: sem sombra, a
superfície define a linha.*

## Ícones de app

Ícones verdadeiros vêm do índice, re-alojados, e são servidos pelo mesmo host do
catálogo. Quando uma app não tem ícone (a Molly, o Cromite, o ReVanced Manager e o
Syncthing-Fork, por exemplo, só publicam vetores), desenha-se um **monograma de cor
plana** derivada do nome do pacote. Sem gradientes.

## Restrições não são navegação

Uma licença restritiva é uma **condição**, não um domínio. Por isso não há um
separador para ela no rodapé: o rodapé responde a "o que quero fazer" (ver apps,
jogar, procurar, configurar), e uma restrição legal não é nenhuma dessas coisas.
O que existe é um filtro que só aparece quando há o que filtrar, uma opção para
esconder, e o aviso antes do botão.

Corolário: nunca inventar um destino de navegação para acomodar uma exceção. Se
uma categoria precisar de um separador, é porque é um domínio — e nesse caso tem
conteúdo suficiente para o justificar.

## Estados vazios

Nunca um ecrã vazio sem explicação. Cada lista sem resultados diz porque está
vazia e, quando faz sentido, o que fazer a seguir — por exemplo, o separador de
jogos explica que o catálogo aceita contribuições por pull request.

## O que a app nunca faz

- Instalar ou atualizar sem mostrar o diálogo do sistema.
- Esconder que uma app não corre neste dispositivo, ou que está assinada por outra
  chave do que a versão instalada.
- Mostrar um número de versão ou um tamanho que não corresponda ao APK verificado.
- Esconder que uma licença é restritiva. A nota aparece **antes** do botão de
  instalar, num tom que não é de erro (não é um erro, é uma condição), e a etiqueta
  da lista tem prioridade sobre "fora da Play".
- Pedir permissões sem explicar para que servem, no momento em que servem.
