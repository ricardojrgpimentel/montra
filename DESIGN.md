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
6. **A identidade vive numa superfície que tem um trabalho.** A Montra tem uma cor e
   uma faixa próprias, e ambas dizem alguma coisa que ninguém diria por ela: qual é o
   estado do catálogo. Uma app sem nenhum elemento próprio é uma app que ninguém
   reconhece; um elemento próprio sem função é decoração, e a decoração é o que este
   documento existe para recusar.

## Paleta

Verde porque é a cor do projeto, não porque está na moda. Nada de roxo nem de
ciano sobre fundo escuro. *Evitar: paleta "AI" (gradientes roxos, ciano brilhante),
bege por omissão.*

| Papel | Claro | Escuro |
| --- | --- | --- |
| Primária (ação) | `#2E6B4F` | `#99D5B2` |
| Sobre primária | `#FFFFFF` | `#003920` |
| Contentor primário | `#B4F1CE` | `#12512F` |
| Acento (atenção) | `#8A5A00` | `#E8B06A` |
| Contentor do acento | `#FFDEA6` | `#4A3208` |
| Superfície | `#FBFDF8` | `#101410` |
| Superfície (linha de lista) | `#F4F8F2` | `#181D18` |
| Erro | `#BA1A1A` | `#FFB4AB` |

Duas cores com significado, e só duas: **verde é ação** (instalar, abrir, o que está
selecionado) e **âmbar é atenção** (uma licença que limita, uma atualização à espera,
uma verificação em curso). Nenhuma das duas é decoração, e nenhuma aparece só para
equilibrar um ecrã.

**Todos os papéis que o Material lê estão escritos.** Omitir um não o deixa neutro:
cai no padrão do Material, que é rosa-púrpura. O aviso de licença restritiva era
desenhado sobre `tertiaryContainer` e saía cor de vinho por essa razão exacta.

**Não usamos cor dinâmica do sistema (Material You).** Uma loja tem identidade: o
verde é a Montra, não o papel de parede de quem instalou. É uma decisão, não um
esquecimento.

**Claro, escuro ou o que o sistema estiver a usar — à escolha.** Isto não contradiz o
parágrafo anterior: uma coisa é a *paleta* (que é nossa), outra é a *luz da sala*
(que é de quem segura no telemóvel). A paleta é a mesma nos dois modos e a escolha
fica guardada. Consequência prática: como a escolha pode divergir do sistema, os
ícones da barra de estado não podem continuar a ser decididos pelo sistema — quem
manda neles é o tema em uso, senão ficam ícones claros sobre um fundo claro.

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

Um título de secção é cinzento, excepto quando a secção pede uma decisão — **só
"Atualizações disponíveis" é âmbar**. Se todas as secções fossem coloridas, a cor
deixava de dizer o que quer que fosse.

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
- Folhas de baixo: `24dp` no topo.

Separadores de secção: **uma linha de 1dp** em `outlineVariant`, acima do título. É a
única linha horizontal das listas e existe para duas secções não dependerem só do
espaço — que é o que acontece quando a lista é longa e o título já saiu do ecrã. A
primeira secção não leva linha: não há nada acima dela de que se separe.

*Evitar: raios exagerados que espremem o conteúdo, e a combinação de contorno fino
com sombra larga — escolhe-se a borda **ou** a sombra. Aqui: sem sombra, a
superfície define a linha.*

## O ícone da Montra

A identidade atual é a **Direção 1 do Stitch**, documentada em
[design/stitch-concepts/README.md](design/stitch-concepts/README.md).
`design/stitch.py` converte o SVG aprovado em quatro VectorDrawables: símbolo
da interface, foreground, background com sangria e silhueta monocromática.
Mantém os gradientes e os detalhes dos módulos; sombras e guias de construção
ficam na apresentação. O canvas de 512px ocupa os 72dp visíveis do ícone
adaptativo de 108dp, e o símbolo fica dentro do círculo de segurança de 66dp.
`python3 design/verify-drawable.py stitch-d1` verifica os quatro ficheiros em CI
e localmente. A marca aparece no cabeçalho Apps e no Sobre, acompanhada pelo
nome em texto nativo, com a cor do tema.

A variante debug chama-se **Montra Debug** e usa fundo azul com um distintivo
**DBG** abaixo da prateleira, dentro do círculo de segurança, como no ACCA.
A versão monocromática inclui o mesmo distintivo em recorte, para continuar
identificável com ícones temáticos. Os recursos ficam em `app/src/debug/res` e
são gerados pelo mesmo `design/stitch.py`; a release conserva a identidade final.

### Identidade anterior (referência)

O ícone é uma montra em dois tempos: **três apps expostas numa prateleira** e, por
baixo, **a faixa** — a mesma superfície que a app usa para dizer o estado do catálogo.
Branco plano sobre `#2E6B4F`, sem gradientes, sem sombra e sem cor dinâmica: aqui o
verde é a Montra e não o papel de parede de quem instalou.

**Toda a arte cabe no círculo de segurança de 66dp** — raio de 33dp a partir do centro
do viewport de 108dp. A regra é o círculo e não o quadrado, e é isso que a torna fácil
de falhar: um retângulo de 60dp passa no quadrado e sai do círculo. O ícone anterior
(aquele círculo com uma haste) subia a 40dp no topo e descia a 36,9dp no canto da barra
de baixo: as máscaras circulares do launcher cortavam-lhe a cabeça e o símbolo deixava
de se ler. Não foi um detalhe de gosto — era um ícone partido, e a correção está
medida, não estimada.

O desenho não se edita à mão: gera-se, e confirma-se.

| Ficheiro | Papel |
| --- | --- |
| `design/icons.py` | a geometria — uma só fonte para o SVG e para o VectorDrawable |
| `design/render.py` | o motor de renderização (chrome-headless-shell) que o ícone e o logo partilham |
| `design/render-previews.sh` | pré-visualiza com a máscara circular, a 512px e a 48px |
| `design/verify-drawable.py` | confirma que o drawable instalado é o conceito aprovado — e, localmente, compara os desenhos píxel a píxel |
| `design/wordmark.py` | o logo: a marca e o nome em contornos, mais os PNG @2x |
| `design/loja/` | os dois PNG da ficha da loja: o ícone de 512 e o destaque de 1024×500 |

Trocar de conceito é `python3 design/icons.py --android d3`, e a verificação responde
com o número de píxeis diferentes — que tem de ser zero. A primeira parte dessa
verificação corre em CI (`android.yml`), antes de tudo o resto: não precisa de Java,
de SDK nem de browser, e é ela que impede um ícone editado à mão de chegar ao
telefone. Um logotipo que se gera também se confere — senão a geração é só uma
maneira mais bonita de o perder.

Para a ficha da loja, `python3 design/icons.py --png d3` escreve um 512×512 da **área
visível** (os 72dp), centrado na arte. A loja não aplica a máscara do launcher e não
herda a subida óptica que o círculo pede: exportar os 108dp inteiros dava uma arte
pequena num quadrado grande.

O destaque de 1024×500 é o logo sobre a superfície da casa
(`python3 design/wordmark.py --loja`) — sem slogan inventado e sem uma terceira
versão da marca. Um gráfico promocional é a única superfície onde apetece inventar
uma frase; a frase não existe, e um logo sozinho não mente.

## O wordmark

O logo atual para documentação é
[montra-wordmark-stitch.svg](design/stitch-concepts/montra-wordmark-stitch.svg),
com uma exportação PNG incluída para manter a tipografia estável no README.
As regras e ficheiros abaixo descrevem a identidade anterior.

O nome **não é texto**: são contornos. Um logo escrito com `font-family` muda de forma
conforme a fonte que existir na máquina de quem abre o ficheiro, e isso não é um logo,
é uma sugestão. Os contornos vêm do Roboto Flex no peso 500 — a mesma família da
interface, para o nome não parecer colado de fora — convertidos uma vez e guardados no
SVG. O ficheiro abre igual em qualquer lado e não arrasta a fonte atrás.

A marca ao lado do nome é a geometria do ícone, e o quadrado verde vale pela **área
visível** do ícone adaptativo, os 72dp, e não pelos 108dp: é isso que faz a marca
ler-se do mesmo tamanho no logo e no ícone. Escalada por 108, a marca saía acanhada ao
pé do nome — foi o que a primeira versão fez, e é o mesmo erro que o ícone antigo
cometia ao contrário.

Duas versões, e só duas: [montra-wordmark.svg](design/wordmark/montra-wordmark.svg)
para fundo claro e
[montra-wordmark-invertido.svg](design/wordmark/montra-wordmark-invertido.svg) para
fundo escuro. Uma superfície nova não inventa uma terceira.

## Ícones de app

Ícones verdadeiros vêm do índice, re-alojados, e são servidos pelo mesmo host do
catálogo.

Quando uma app não tem ícone — a Molly, o Cromite, o ReVanced Manager e o
Syncthing-Fork, por exemplo, só publicam vetores — desenha-se um **monograma de cor
plana** derivada do nome do pacote. Sem gradientes.

O monograma **preenche a caixa do ícone**: a letra ocupa cerca de 42% do lado, como
a arte de um ícone adaptativo dentro da sua margem. Isto já esteve errado — a caixa
encolhia até ao tamanho da letra, e um monograma saía como um selo de 20dp ao lado
de ícones de 52dp. Era a razão principal pela qual as apps sem logotipo pareciam
avariadas, e é uma regra, não um detalhe de implementação.

As cores dos monogramas são as oito da paleta: verde, azul-petróleo, verde-acinzentado,
oliva, castanho, azul-ardósia, âmbar escuro e verde-musgo. **Nada de roxo**: um
monograma é um espaço reservado, não uma licença para sair da paleta.

## A faixa da montra

Debaixo do título, uma faixa de uma linha diz o que o catálogo tem e quando foi
confirmado pela última vez:

    2 atualizações · 42 apps · 4 instaladas        verificado há 4 minutos

É a única superfície de largura inteira com a cor da casa em vez de um cinzento, e é
a assinatura da app. A cor tem um trabalho: **enquanto a faixa estiver verde o
catálogo está confirmado; passa a vermelho quando a última tentativa falhou**, com o
motivo e um "Tentar de novo" na própria faixa, em vez de escondidos nas definições. O
âmbar aparece na contagem de atualizações, que é a única parte da linha que pede uma
decisão.

São **duas** as cores de falha, porque são duas as coisas diferentes que podem correr
mal. Um índice recusado — assinatura inválida, chave que não bate — é grave e fica a
vermelho. Estar sem rede é uma condição, não uma avaria: a faixa fica no cinzento da
faixa recolhida, diz **"Sem ligação à internet · catálogo verificado"** e o "Tentar de
novo" continua lá. Antes de escrever fosse o que fosse, a app pergunta ao sistema se
há rede — a mensagem é uma afirmação sobre o telemóvel de quem lê, e não sobre o
servidor (o ecrã nunca mostrou `UnknownHostException: raw.githubusercontent.com`,
que fala do sítio errado e não se resolve em lado nenhum). Quando a rede volta, a
verificação é repetida sozinha: tirar o modo avião resolve a faixa sem se lhe tocar.

Enquanto se verifica, o lado direito passa a "a verificar…" com um indicador; quando
termina, diz o resultado durante uns segundos ("catálogo atualizado", "já estava
atualizado") e volta a "verificado há X". Nunca há um estado invisível: foi
exactamente por isso que esta faixa existe — antes havia um ícone de refresh no canto
do ecrã, sem spinner, sem data e sem erro.

Quando a lista rola, **a faixa recolhe**: perde a cor e passa a cinzenta. Em cima é a
assinatura do catálogo; a partir do primeiro scroll é só mais uma barra a competir
com o conteúdo.

## A voz

Português de Portugal, tratamento por "tu", e nada da voz de montra de loja de
aplicações: nem "poderoso", nem "intuitivo", nem "a melhor forma de". Quem lê isto
já sabe o que é uma loja de apps.

- **Cada controlo explica porque existe.** Um interruptor sem a frase que diz o que
  ele decide é um botão que ninguém sabe se deve tocar. "Esconder apps com licença
  restritiva" não chega: falta o que isso faz ao catálogo e quem fica de fora.
- **Rótulos em minúscula, frases em maiúscula.** `versão`, `assinatura`, `chave de
  confiança` são metadados e vivem numa tabela; "O Android ainda não autorizou…" é
  uma frase e vive no texto.
- **O erro diz o que fazer.** "Índice recusado" é um diagnóstico; sem a saída ao
  lado, é só má notícia.
- **Um número vale mais do que um adjetivo.** "48 apps", "desde 9 de abril de
  2023", "2,5 MB". É o que torna uma frase verificável em vez de persuasiva.
- **Nada de exclamações**, e nada de duas frases onde uma chega.
- **A versão diz-se por inteiro.** Nome e `versionCode`, juntos, em "Sobre": é o
  que se lê em voz alta quando alguém reporta um problema.

## Uma app parada

Uma app sem lançamentos há mais de **seis meses** ganha um aviso âmbar: o
distintivo "sem lançamentos" na linha e um bloco na ficha com **a data do último
lançamento** — "Sem lançamentos no catálogo desde 23 de fevereiro de 2026".

**O aviso é sobre o que o catálogo vê, e diz isso.** O catálogo só conhece as
releases que o projeto publica no GitHub; o F-Droid, por exemplo, publica primeiro
no GitLab e o espelho do GitHub fica para trás. "Sem atualizações" seria uma
afirmação sobre o projeto, que o catálogo não tem como fazer; "sem lançamentos no
catálogo" é uma afirmação sobre o que ele sabe — e essa confirma-se no repositório
que a ficha liga.

**Âmbar, não vermelho.** Uma app parada não é uma avaria nem um erro de ninguém: é
uma condição que muda a decisão de quem vai instalar, e as condições deste catálogo
são âmbar — uma licença que limita, uma atualização à espera. O vermelho continua
reservado ao que corre mal, e uma app que ninguém lançou não corre mal por isso.

**A data é o que torna o aviso verificável.** "Sem lançamentos" sozinho é uma
opinião; "desde 23 de fevereiro de 2026" é um facto que a pessoa confirma no
repositório. É por isso que a data vai no aviso, e não uma contagem de meses.

O limiar é estrito: **mais** de seis meses, não seis meses. E sem data no índice
não há aviso nenhum: não se acusa uma app de estar parada sem saber quando é que
ela lançou.

Na linha, o distintivo entra na ordem de prioridade que já existe — não corre aqui,
assinatura diferente, licença restritiva, **sem lançamentos**, fora da Play — e
uma linha continua a mostrar **um** distintivo só. O "fora da Play" cede o lugar
porque é o único dos cinco que quem entra nesta loja já sabe, e que os filtros
continuam a responder.

O aviso não tem estado próprio: sai de `release.publishedAt`, que o índice já
publica. Uma data não envelhece numa cache; uma bandeira "parada" gravada ontem
envelhece.

## Filtros: dois controlos, três respostas

A primeira versão punha quatro coisas diferentes no mesmo fato — a ordenação (que é
um menu), dois filtros do catálogo e dezoito categorias — todas com o mesmo chip. Não
havia hierarquia, e a única forma de saber o que estava escolhido era olhar para a cor
de um chip que já tinha saído do ecrã, porque a fila fazia scroll na horizontal.

Um controlo só pode ser uma de duas coisas:

- **Ordenar é um menu.** Texto simples com uma seta, sem moldura. Não guarda estado:
  muda a ordem e sai de cena.
- **Filtrar é um estado.** Ganha moldura quando está vazio e preenchimento quando tem
  algo ligado, e diz quantos filtros estão ativos ("Filtrar · 2").

E há uma terceira resposta, que era a que faltava: **os filtros ativos ficam à vista
como peças removíveis, fora da lista**. Não desaparecem com o scroll porque não vivem
dentro dele. Quem chega a meio de uma lista filtrada vê sempre o que a está a filtrar,
e tira o filtro sem ter de procurar onde é que ele se esconde.

Regra: um filtro só aparece se apanhar alguma coisa. As contagens vivem ao lado de
cada opção, na folha, para não ser preciso carregar para descobrir que não há nada.

## Verificação do catálogo

O catálogo é um ficheiro num repositório: **não há servidor para avisar que saiu uma
versão nova**. Há três formas de perguntar, e todas usam o ETag — quando nada mudou,
a resposta são uns bytes:

1. **Puxar a lista** (`pull to refresh`) nos separadores Apps, Jogos e Procura.
2. **Ao abrir a app**, se já passou o intervalo escolhido. A cadência mede o intervalo
   *mínimo* entre verificações, não uma espera obrigatória.
3. **Um temporizador** enquanto a app está à frente, com a cadência das definições
   (desligado, 15 minutos, 1 hora, 3 horas, um dia). Fora de primeiro plano não se
   verifica nada.

A verificação explícita continua nas definições, junto da origem, da data e da chave
de confiança. O que não existe é um ícone de refresh solto numa barra de topo.

## Movimento

O movimento explica uma mudança; não chama atenção. Quatro regras:

- As linhas da lista **animam para o lugar** quando um filtro ou a ordenação mudam:
  é isso que torna visível o que a lista fez.
- Trocar de separador é uma dissolvida curta; **abrir uma app desliza**, porque é uma
  ida a algum lado e não uma substituição.
- A faixa recolhe com uma transição de cor, não com um salto.
- Um progresso mexe-se porque há progresso. Nada pulsa.

Durações: 120 ms para o que só confirma (fade de saída), 240 ms para o que explica,
400 ms no máximo. Curva `FastOutSlowInEasing`. Sem `bounce` e sem `spring` à vista.

## Um filtro tem de responder a uma pergunta

Um filtro só existe se alguém o fizer. **Fora da Play Store** responde — é a razão
de ser desta loja. **Licença restritiva** responde — quem se importa com licenças
procura-o. **"Só forks"** não responde a nada: ninguém escolhe uma aplicação por ser
um fork, escolhe-a por substituir outra que já usa.

O parentesco é **contexto da ficha**, não um eixo de navegação: a linha "Baseado em
X" diz o que a app substitui e, quando o original está no catálogo, leva lá — é
assim que alguém compara os dois sem sair da loja. E escreve-se apenas quando o
próprio projeto o declara; parentesco inventado é pior do que parentesco ausente.

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

E nunca um beco: um ecrã vazio por causa de um filtro tem um botão que o tira, e um
ecrã vazio por causa de uma falha de rede tem um botão que tenta outra vez.

## O que a app nunca faz

- Instalar ou atualizar sem mostrar o diálogo do sistema.
- Esconder que uma app não corre neste dispositivo, ou que está assinada por outra
  chave do que a versão instalada.
- Mostrar um número de versão ou um tamanho que não corresponda ao APK verificado.
- Esconder que uma licença é restritiva. A nota aparece **antes** do botão de
  instalar, num tom que não é de erro (não é um erro, é uma condição), e a etiqueta
  da lista tem prioridade sobre "fora da Play".
- Pedir permissões sem explicar para que servem, no momento em que servem.
- Verificar o catálogo sem o dizer. Uma verificação em curso, o resultado da última e
  a data da última confirmada estão sempre na faixa — nunca um ícone que não se sabe
  se está a fazer alguma coisa.
- Deixar o teclado tapar a barra de navegação: com o IME à frente, o rodapé sai.
