#!/usr/bin/env bash
# Tira as capturas de ecrã da app para a ficha e para o README.
#
# Corre num emulador descartável, criado fora dos repositórios e assente no
# system image que já está instalado — o telemóvel de quem desenvolve não entra
# nisto, e é isso que impede as capturas de levar para o repositório público as
# apps instaladas e as notificações de quem as tirou.
#
#   ./design/capturas.sh              # cria o AVD, instala e captura
#   ./design/capturas.sh --manter     # não apaga o AVD no fim
#
# As coordenadas dos toques são de um perfil pixel_6 (1080x2400). Se o ecrã da
# app mudar de sítio, o sítio certo vê-se numa captura: tira-se uma, olha-se, e
# ajusta-se o número. Não há aqui nada mais inteligente do que isso.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
AVD_HOME="${AVD_HOME:-$(cd "$ROOT/.." && pwd)/.avd-local}"
AVD_NAME="capturas"
SERIAL="emulator-5554"
SAIDA="$ROOT/fastlane/metadata/android/en-US/images/phoneScreenshots"
MANTER=0
[ "${1:-}" = "--manter" ] && MANTER=1

export ANDROID_AVD_HOME="$AVD_HOME"
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export PATH="$SDK/emulator:$SDK/platform-tools:$PATH"
ADB="adb -s $SERIAL"

if [ ! -f "$AVD_HOME/$AVD_NAME.ini" ]; then
  mkdir -p "$AVD_HOME"
  echo no | "$SDK/cmdline-tools/latest/bin/avdmanager" create avd \
    -n "$AVD_NAME" -k "system-images;android-36;google_apis_playstore;arm64-v8a" \
    -d pixel_6 --force
fi

emulator -avd "$AVD_NAME" -no-window -no-audio -no-boot-anim -no-snapshot \
  -gpu swiftshader_indirect > /tmp/emu-capturas.log 2>&1 &
echo "à espera que o emulador arranque…"
adb -s "$SERIAL" wait-for-device
until [ "$($ADB shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 3; done

$ADB install -r "$ROOT/app/build/outputs/apk/debug/app-debug.apk"
# Sem esta autorização, o primeiro ecrã é o aviso "falta uma autorização" em vez
# da loja. É uma autorização por app, e no emulador pode dar-se por adb.
$ADB shell appops set dev.montra.debug REQUEST_INSTALL_PACKAGES allow

# Barra de estado limpa: hora fixa, bateria cheia, sem notificações.
$ADB shell settings put global sysui_demo_allowed 1
for c in "enter" "clock -e hhmm 0900" "battery -e level 100 -e plugged false" \
         "network -e wifi show -e level 4" "notifications -e visible false"; do
  $ADB shell am broadcast -a com.android.systemui.demo -e command $c > /dev/null
done

capturar() { sleep "${2:-3}"; mkdir -p "$SAIDA"; $ADB exec-out screencap -p > "$SAIDA/$1.png"; echo "  $1.png"; }

mkdir -p "$SAIDA"
$ADB shell am force-stop dev.montra.debug
$ADB shell am start -n dev.montra.debug/dev.montra.MainActivity > /dev/null

echo "a capturar:"
capturar 1 8                                    # catálogo
$ADB shell input tap 500 1500 && capturar 2 3   # ficha de uma app
$ADB shell input keyevent 4                     # voltar
$ADB shell input tap 443 2200 && capturar 3 3   # jogos e emuladores
$ADB shell input tap 723 2200                   # procurar
$ADB shell input tap 540 247                    # campo de procura
$ADB shell input text "emulador" && sleep 3
$ADB shell input keyevent 4 && capturar 4 2     # resultados, sem teclado
$ADB shell input tap 943 2200 && capturar 5 3   # definições

$ADB shell am broadcast -a com.android.systemui.demo -e command exit > /dev/null
$ADB shell settings put global sysui_demo_allowed 0

adb -s "$SERIAL" emu kill
if [ "$MANTER" -eq 0 ]; then
  sleep 3
  rm -rf "$AVD_HOME"
fi
echo "capturas em $SAIDA"
