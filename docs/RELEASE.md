# Assinatura e releases da Montra

A identidade Android é `dev.montra`. A chave de publicação assina o APK da
Montra; é independente da chave ECDSA que assina o catálogo `montra-index`.
As atualizações do APK distribuído diretamente têm de manter a mesma chave.

Certificado de publicação criado em 9 de outubro de 2026: `CN=Montra`, RSA 4096,
válido até 9 de outubro de 2056. Impressão SHA-256:

```text
39:84:F0:C2:3E:3F:A0:49:31:A6:D9:EC:41:7A:6E:61:91:C2:31:28:77:6C:41:F7:E9:28:67:C5:AC:5D:34:C3
```

## Configuração local

Usa JDK 21, Android SDK 36 e build-tools 36.0.0. Define `JAVA_HOME` e
`ANDROID_HOME` de acordo com a instalação nesse computador.

Mantém o keystore JKS e o ficheiro de propriedades numa pasta privada fora do
repositório. Copia `keystore.properties.example` para essa pasta com o nome
`keystore.properties` e preenche as duas palavras-passe. O alias da chave criada
é `montra-release`. `storeFile=montra-release.jks` resolve relativamente à pasta
do ficheiro de propriedades, pelo que ambos podem ser movidos juntos.

```bash
export MONTRA_KEYSTORE_PROPERTIES="/caminho/privado/montra/keystore.properties"
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease :app:bundleRelease --console=plain
```

Em PowerShell, usa `$env:MONTRA_KEYSTORE_PROPERTIES = "C:/caminho/privado/montra/keystore.properties"`
e `./gradlew.bat`. Em alternativa, coloca um `keystore.properties` na raiz do
projeto (ignorado pelo Git). Usa `/` nos caminhos dentro do ficheiro de propriedades.

Os artefactos são:

- APK instalável: `app/build/outputs/apk/release/app-release.apk`.
- AAB para upload: `app/build/outputs/bundle/release/app-release.aab`.
- Mapeamento R8 para diagnosticar crashes desta versão: `app/build/outputs/mapping/release/mapping.txt`.

Confirma o certificado do APK antes de publicar:

```bash
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
sha256sum app/build/outputs/apk/release/app-release.apk app/build/outputs/bundle/release/app-release.aab
```

A impressão SHA-256 do **certificado** deve corresponder à guardada no Bitwarden.
O SHA-256 do **ficheiro APK** muda em cada build e não identifica a chave.
O AAB pode ser verificado com `jarsigner -verify -verbose -certs` e o certificado
consultado com `keytool -printcert -jarfile app-release.aab`.

## Guardar no Bitwarden

Cria uma nota segura «Montra — assinatura Android de release» e guarda:

- O conteúdo completo de **`montra-release.jks` em Base64**, junto das credenciais
  na mesma nota. O Base64 representa todos os bytes do JKS e dispensa anexos.
  Em alternativa, guarda o ficheiro JKS como anexo. Guardar apenas as
  palavras-passe ou o certificado público não permite voltar a assinar.
- `storePassword`, `keyAlias` e `keyPassword`, copiados do ficheiro privado.
- Tipo de keystore `JKS`, pacote `dev.montra`, algoritmo e validade.
- Impressão SHA-256 do certificado, para confirmar a identidade após restauro.
- O ficheiro `keystore.properties` como anexo opcional, já que contém as mesmas
  credenciais da nota; o caminho é relativo e portátil.

Mantém também uma segunda cópia de segurança encriptada da nota completa ou do
JKS com as credenciais. Não publiques a chave, as palavras-passe
ou a nota de recuperação em Git, issues, releases ou registos de CI.

## Restaurar noutro computador

1. Descodifica o Base64 guardado na nota para `montra-release.jks` numa pasta
   privada, ou descarrega o JKS se o guardaste como anexo. Em Linux:
   `base64 --decode keystore-base64.txt > montra-release.jks`, onde o ficheiro
   de entrada contém apenas o Base64, sem os marcadores BEGIN/END.
2. Restaura `keystore.properties` nessa pasta, ou recria-o a partir da nota.
3. Instala JDK 21 e o SDK indicado acima; clona o projeto.
4. Define `MONTRA_KEYSTORE_PROPERTIES` e compila com os comandos acima.
5. Verifica que o certificado do APK corresponde à impressão guardada.

Não cries uma nova chave para uma atualização. Antes de cada publicação,
aumenta `versionCode` e define `versionName` em `app/build.gradle.kts`; conserva
também o APK, o AAB e o mapeamento R8 dessa release.

## CI e Google Play

A CI atual não recebe a chave de publicação e usa
`-PmontraUnsignedRelease=true` para verificar a compilação minificada. Esta
opção é só para validação: os artefactos não são assinados para distribuição.

Se escolheres Google Play, configura Play App Signing antes da primeira
publicação. O AAB é assinado com a chave de upload; o Play assina os APKs que
distribui com a chave de assinatura da app. Para manter a identidade entre o
APK direto e o Play, fornece esta chave de assinatura durante a configuração,
em vez de escolher uma chave diferente gerada pelo Google. A configuração
da conta e da loja é um passo separado da compilação local.

Referência: [Assinar a app — Android Developers](https://developer.android.com/studio/publish/app-signing).
