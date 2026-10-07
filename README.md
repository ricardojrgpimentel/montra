# Montra — cliente Android

A app. Descarrega um catálogo, verifica a assinatura, verifica cada APK, instala.

> Visão geral do sistema (porque é que isto funciona sem servidor, modelo de
> confiança, estado verificado): [montra-index/docs/OVERVIEW.md](https://github.com/ricardojrgpimentel/montra-index/blob/main/docs/OVERVIEW.md).
> O catálogo vive em [montra-index](https://github.com/ricardojrgpimentel/montra-index).

- **Kotlin + Jetpack Compose**, minSdk 26, targetSdk 36
- **Sem bibliotecas de UI de terceiros**: Material 3 e um carregador de imagens
  próprio (cache de memória + cache HTTP em disco). O APK de release tem 1,6 MB.
- **Sem injeção de dependências por framework**: um `AppContainer` com sete objetos.
- **Sem Room**: o catálogo inteiro cabe em memória; a persistência é o ficheiro
  verificado em disco mais DataStore para as preferências.

## Compilar

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"   # JDK 17+
export ANDROID_HOME="$HOME/Library/Android/sdk"                                  # SDK 36 + build-tools 36.0.0

./scripts/sync-index-assets.sh    # índice + chave pública → app/src/main/assets
./gradlew :app:testDebugUnitTest  # 15 testes, incluindo a assinatura real do índice
./gradlew :app:assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease    # minificado com R8 (sem assinatura de publicação)
./gradlew :app:lintDebug
```

Para publicar um release é preciso um keystore: cria `keystore.properties` (ignorado
pelo git) com `storeFile`, `storePassword`, `keyAlias`, `keyPassword` e configura
`signingConfigs.release` em `app/build.gradle.kts`.

## Estrutura da navegação

Quatro destinos, no rodapé, como a Play Store — que é a estrutura que as pessoas já
têm na cabeça:

| Separador | O que mostra |
| --- | --- |
| **Apps** | tudo o que o catálogo tem, em secções: atualizações, instaladas, o resto |
| **Jogos** | jogos e emuladores (as categorias `games` e `emulators`); vazio, explica que se contribui por pull request |
| **Procurar** | campo próprio com foco automático e sugestões quando está vazio |
| **Definições** | origem do catálogo, chave de confiança, estado da verificação, URL configurável |

O rodapé desaparece na página de uma app: essa página tem a sua própria ação
principal e o seu próprio regresso. A barra de pesquisa dos separadores Apps e
Jogos navega para o separador Procurar, em vez de haver dois filtros para a mesma
coisa em sítios diferentes.

### Licenças restritivas: filtro, não separador

O catálogo aceita apps cujo código é público mas a licença impõe limitações
(`LicenseRef-*`), desde que declaradas. Isso **não** ganhou um separador próprio,
e a razão é de desenho: o rodapé é organizado por domínio (apps, jogos, procurar,
definições), não por estado legal. Um quinto destino para um contentor com uma app
seria peça de interface permanente para uma lista quase sempre vazia, e
transformaria um aviso em navegação.

Em vez disso:

- filtros nos chips do separador Apps — **Fora da Play Store** e **Licença
  restritiva** — cada um só aparece se apanhar alguma coisa;
- o parentesco declarado pelo projeto aparece na ficha como **"Baseado em X"**, com
  ligação à ficha do original quando ele também está no catálogo (PipePipe →
  NewPipe). Não é um filtro de propósito: ninguém escolhe uma app por ser fork;
- uma opção **"Esconder apps com licença restritiva"** nas definições, por omissão
  desligada — o catálogo decidiu incluí-las, e esconder por omissão seria decidir
  pelo utilizador;
- e o trabalho de fundo: etiqueta na lista, bloco antes do botão de instalar, nota
  no idioma do sistema.

Note-se a diferença entre os dois avisos, que não são a mesma coisa: `nonFreeNet`
é software **livre** que fala com uma rede proprietária (Nekogram, Nagram,
ReVanced Manager); `restrictedLicense` é software cujo **código** não é livre
(DevilutionX).

## Design

As decisões visuais estão em [DESIGN.md](DESIGN.md): paleta, escala de tipografia,
escala de espaço, formas, e o que a app nunca faz. O código referencia essas
constantes (`ui/theme`) em vez de inventar valores por composable. O ficheiro foi
escrito a seguir a ler as regras do [Impeccable](https://impeccable.style/slop) e
anota, em cada regra que nos toca, porque é que ela existe.

## Estrutura

```
app/src/main/java/dev/montra/
├── MontraApp.kt            Application + AppContainer (wiring manual)
├── MainActivity.kt            NavHost: lista, detalhe, definições
├── security/
│   ├── IndexVerifier.kt       ECDSA P-256 sobre os bytes do índice; key id
│   ├── TrustStore.kt          a chave pública incluída no APK
│   └── ApkVerifier.kt         sha256 (streaming) + certificado do APK
├── data/
│   ├── model/Index.kt         modelo serializável + bestAssetFor(abis)
│   ├── IndexSource.kt         rede com ETag, cache, assets, URLs relativas
│   ├── IndexRepository.kt     verificar → publicar; política de falha
│   └── Settings.kt            DataStore
├── install/
│   ├── ApkDownloader.kt       download com sha256 em streaming
│   └── InstallManager.kt      verifica antes da sessão; PackageInstaller
└── ui/                        Compose: lista, detalhe, definições, imagens
app/src/main/assets/
├── index.json                 snapshot do catálogo (funciona offline no 1.º arranque)
├── index.json.sig             assinatura do snapshot
└── index-signing.pub.pem      chave de confiança (parte da identidade da app)
```

## Permissões, progresso e notificações

Duas coisas que uma loja tem de fazer bem, porque são onde o utilizador desiste:

**A autorização para instalar.** O Android exige uma autorização *por app*
("Instalar apps desconhecidas"). Se ela faltar, nada é transferido: a app verifica
primeiro, mostra uma faixa no topo da lista e transforma o botão de cada app de
"Instalar" em "Autorizar", que abre diretamente o ecrã do sistema onde o
interruptor está. Um erro a vermelho depois de 15 MB descarregados era a versão
anterior — e era má.

**O progresso.** O download corre num serviço em primeiro plano
(`install/InstallService`, tipo `dataSync`), não no ViewModel: um APK de 300 MB não
pode morrer quando o utilizador sai da app, e é isso que permite a notificação.

- Canal `downloads`, importância `LOW`: visível e atualizável, nunca sonoro.
- Mostra bytes reais e percentagem, depois "A verificar…", "Confirma a instalação"
  e por fim "instalada" ou a razão da falha.
- Tocar abre a app no ecrã dessa aplicação (o `InstallRequest` viaja no Intent e a
  MainActivity faz o deep link, com `singleTop` para não reiniciar).
- Traz uma ação **Cancelar** que aborta o `Call` do OkHttp — cancelar a corrotina
  não chega, porque a leitura do socket é uma chamada bloqueante.
- O estado vive num único sítio (`InstallManager`), pelo que o ecrã e a
  notificação não podem discordar.

A permissão `POST_NOTIFICATIONS` é pedida no momento em que passa a ser útil (no
primeiro toque em Instalar). Recusá-la não bloqueia a instalação: o progresso
continua visível no ecrã.

### O diálogo do sistema

Uma sessão de `PackageInstaller` que precise de confirmação devolve
`STATUS_PENDING_USER_ACTION` **com um Intent** que a app tem de lançar. Ignorar isto
não dá erro: a instalação fica pendurada à espera de um diálogo que ninguém mostra.
Foi exatamente o que se observou num Android 16, e é por isso que o
`InstallResultReceiver` lança o Intent sempre que ele venha — e trata também o
caminho pré-Android 12, onde era obrigatório.

## Fluxo de instalação

```
utilizador carrega em Instalar
        │
        ├─ falta autorização para instalar apps desconhecidas? → botão que a resolve, sem download
        ├─ pedido vai para o InstallService (primeiro plano, notificação de progresso)
        │
        ├─ asset = bestAssetFor(Build.SUPPORTED_ABIS)   sem APK compatível → diz-se, não se instala
        ├─ minSdk acima desta API → "incompatível", não se tenta
        │
        ├─ download com SHA-256 calculado em streaming
        │     └─ não corresponde ao índice → ficheiro apagado, erro explícito
        │
        ├─ certificado do APK lido com PackageManager
        │     └─ não corresponde ao pin do índice → erro explícito
        │
        ├─ permissão "instalar apps desconhecidas" verificada
        │
        └─ PackageInstaller: sessão → escrita do ficheiro → commit
              └─ o diálogo do sistema é mostrado sempre; nada é silencioso
```

O estado de cada app (`Idle`, `Downloading`, `Verifying`, `AwaitingUser`,
`Installed`, `Failed`) vive num `StateFlow` em `InstallManager` e é atualizado pelo
`InstallResultReceiver`, declarado no manifest e não exportado.

## Testes

```bash
./gradlew :app:testDebugUnitTest                            # 15 testes, JVM, sem rede
./gradlew :app:connectedDebugAndroidTest                    # 3 testes num dispositivo/emulador com rede
```

| Ficheiro | O que prova |
| --- | --- |
| `security/IndexVerifierTest` | assinatura válida aceite; um byte alterado, outra chave ou base64 malformado recusados; key id estável |
| `data/RealIndexTest` | **os bytes reais do índice incluído na app verificam com o verificador real**; key id bate certo; bytes adulterados recusados; todas as apps têm release, sha256, certificado e ícone relativo |
| `data/IndexModelTest` | campos desconhecidos ignorados; fallback de idioma; escolha de ABI |
| `androidTest/CatalogueNetworkSmokeTest` | **no dispositivo, no processo da app**: descarrega o índice publicado por HTTPS, verifica a assinatura com a chave do APK, guarda em cache, e recusa um índice adulterado (com uma fonte hostil injetada); o `InstallRequest` sobrevive à passagem por Intent sem perder o sha256 nem o certificado; e cancelar a meio de um download para o pipeline, deixa o estado em `Idle` e não deixa ficheiros parciais |

O `RealIndexTest` liga o assinador (Node, `tools/sign-index.mjs`) ao verificador
(Kotlin): se qualquer dos lados mudar de formato, o build falha. O teste
instrumentado fecha o resto do caminho — rede, cache, e a recusa de um índice
adulterado — no sítio onde interessa.

### Correr os testes instrumentados

`./gradlew :app:connectedDebugAndroidTest` reinstala a app a cada corrida, e
reinstalar (ou desinstalar) repõe o appop "instalar apps desconhecidas" — o teste
do cancelamento precisa dele e, sem ele, é **saltado** com a razão à vista (não
falha por timeout sem explicação). Para o correr a sério, sem reinstalar:

```bash
./gradlew :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell appops set dev.montra.debug REQUEST_INSTALL_PACKAGES allow
adb shell am instrument -w \
  -e class 'dev.montra.CatalogueNetworkSmokeTest#cancellingADownloadStopsItAndLeavesNothingBehind' \
  dev.montra.debug.test/androidx.test.runner.AndroidJUnitRunner
```

## Registos

`adb logcat -s Montra` conta a história completa de uma atualização: URL, que
fonte ganhou (rede, cache ou snapshot incluído), se a assinatura verificou e
porquê quando não verificou. Falhas são registadas também em release; o detalhe
verboso é só em debug.

## Decisões que valem a pena conhecer

- **`QUERY_ALL_PACKAGES`** é pedido porque uma loja tem de saber "isto está
  instalado? que versão?". O catálogo é dinâmico, por isso uma lista `<queries>`
  não é possível.
- **`minSdk 26`** porque `java.util.Base64` e as sessões de `PackageInstaller`
  existem a partir daí; não vale a pena manter compatibilidade com menos.
- **Conflito de assinatura**: se a app já está instalada assinada por outra chave
  (por exemplo a versão da F-Droid), o Android recusa a atualização. A app deteta
  e explica, em vez de falhar de forma obscura, e oferece a desinstalação.
- **O URL do índice é configurável** nas Definições e tem de ser HTTPS. Apontar
  para outro índice passa a app a ser uma loja diferente — a chave de confiança,
  essa, não é configurável (vem no APK).
