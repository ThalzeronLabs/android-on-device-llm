#!/usr/bin/env bash
# Série do Gallery DENTRO do app (sem sair e sem reinicializar o modelo): antes de cada rodada, espera a CPU < 50 °C com o
# Gallery parado na tela; "Nova conversa" pelo histórico; envia; espera fixa de 90 s; guarda 2 capturas (antes e depois do
# botão das estatísticas). Nada lê a tela durante a geração. Uso: serie-gallery.sh <rodadas> <rótulo>
export MSYS_NO_PATHCONV=1
DIR="$(cd "$(dirname "$0")" && pwd)"
A="adb -s ${SERIAL:?set SERIAL to the adb serial of the phone}"; LIMITE=50; ESPERA=${ESPERA:-90}
cpu() { $A shell dumpsys thermalservice | grep -E 'mName=CPU, mStatus' | tail -1 | grep -oE 'mValue=[0-9.]+' | cut -d= -f2; }
bat() { $A shell dumpsys battery | grep -E '^  (temperature|USB powered)' | sed 's/^ *//' | tr '\n' ' '; }
amostra() { $A shell 'echo "status=$(dumpsys thermalservice | grep -m1 "Thermal Status" | cut -d: -f2 | tr -d " ") f4=$(cat /sys/devices/system/cpu/cpufreq/policy4/scaling_cur_freq) f7=$(cat /sys/devices/system/cpu/cpufreq/policy7/scaling_cur_freq)"'; }
esfria() { for i in $(seq 1 40); do T=$(cpu); [ "${T%.*}" -lt $LIMITE ] && break; $A shell sleep 15; done; }
PEDIDO="Write%sa%s300%sword%sstory%sabout%sa%srobot%swho%slearns%sto%spaint"
for r in $(seq 1 ${1:-3}); do
  esfria; C=$(cpu); B=$(bat)
  $A shell input tap 1147 220; $A shell sleep 2; $A shell input tap 435 462; $A shell sleep 3     # histórico → Nova conversa
  $A shell input tap 450 2453; $A shell sleep 1; $A shell input text "$PEDIDO"; $A shell sleep 2
  if $A shell dumpsys input_method | grep -q 'mInputShown=true'; then $A shell input tap 1072 1770; else $A shell input tap 1072 2594; fi
  $A shell sleep 5; S=$(amostra); $A shell sleep $((ESPERA-5))
  F="$DIR/gallery-$2-r$r"; $A exec-out screencap -p > "$F-a.png"
  $A shell input tap 317 2594; $A shell sleep 2; $A exec-out screencap -p > "$F-b.png"
  echo "$2 rodada $r GALLERY | cpu $C C | $B| capturas $(basename "$F")-a/-b | $S"
done
