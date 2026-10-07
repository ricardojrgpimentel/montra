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

## Fluxo de instalação

```
utilizador carrega em Instalar
        │
        ├─ asset = bestAssetFor(Build.SUPPORTED_ABIS)   sem APK compatível → diz-se, não se instala
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
| `androidTest/CatalogueNetworkSmokeTest` | **no dispositivo, no processo da app**: descarrega o índice publicado por HTTPS, verifica a assinatura com a chave do APK, guarda em cache, e recusa um índice adulterado (com uma fonte hostil injetada) |

O `RealIndexTest` liga o assinador (Node, `tools/sign-index.mjs`) ao verificador
(Kotlin): se qualquer dos lados mudar de formato, o build falha. O teste
instrumentado fecha o resto do caminho — rede, cache, e a recusa de um índice
adulterado — no sítio onde interessa.

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
