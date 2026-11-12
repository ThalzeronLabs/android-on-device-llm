#!/usr/bin/env bash
# Lado a lado v2 (pedido do Otávio, 11/11 19h08): os dois apps são tratados IGUAIS durante a geração.
# - Nada lê a tela durante a geração: espera um tempo fixo (ESPERA s) e lê UMA vez depois (a tela no PocketPal, o logcat no nosso).
# - No mesmo instante (5 s depois do envio), nos dois apps, uma leitura leve: o Thermal Status e a frequência dos núcleos rápidos.
# - Antes de cada rodada: CPU < LIMITE °C. Registra a bateria (a temperatura e se está na tomada).
# Uso: alternado2.sh <rodadas> <rótulo>
export MSYS_NO_PATHCONV=1
DIR="$(cd "$(dirname "$0")" && pwd)"
A="adb -s ${SERIAL:?set SERIAL to the adb serial of the phone}"; LIMITE=50; ESPERA=${ESPERA:-70}
UI() { $A shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; $A exec-out cat /sdcard/ui.xml; }
centro() { grep -oE "$1[^>]*bounds=\"\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]\"" | head -1 | grep -oE '[0-9]+' | tail -4 | tr '\n' ' '; }
toca() { set -- $(UI | centro "$1"); [ -n "$1" ] && $A shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 )); }
cpu() { $A shell dumpsys thermalservice | grep -E 'mName=CPU, mStatus' | tail -1 | grep -oE 'mValue=[0-9.]+' | cut -d= -f2; }
bat() { $A shell dumpsys battery | grep -E '^  (temperature|USB powered|AC powered)' | sed 's/^ *//' | tr '\n' ' '; }
amostra() { $A shell 'echo "status=$(dumpsys thermalservice | grep -m1 "Thermal Status" | cut -d: -f2 | tr -d " ") f4=$(cat /sys/devices/system/cpu/cpufreq/policy4/scaling_cur_freq) f7=$(cat /sys/devices/system/cpu/cpufreq/policy7/scaling_cur_freq)"'; }
esfria() { $A shell input keyevent HOME; for i in $(seq 1 40); do T=$(cpu); [ "${T%.*}" -lt $LIMITE ] && break; $A shell sleep 15; done; }
PEDIDO="Write%sa%s300%sword%sstory%sabout%sa%srobot%swho%slearns%sto%spaint"
nosso() {
  $A shell am start -n com.thalzeronlabs.ondevicellm.staging/com.thalzeronlabs.ondevicellm.MainActivity >/dev/null 2>&1; $A shell sleep 10
  $A shell input tap 90 221; $A shell sleep 1; toca 'text="New chat"'; $A shell sleep 1
  toca 'class="android.widget.EditText"'; $A shell sleep 1; $A shell input text "$PEDIDO"; $A shell sleep 2; $A logcat -c
  toca 'content-desc="Send"'; $A shell sleep 5; S=$(amostra); $A shell sleep $((ESPERA-5))
  L=$($A logcat -d -s llama-kotlin | grep "generation:" | tail -1)
  echo "${L#*generation: } | $S"
}
pocket() {
  $A shell monkey -p com.pocketpalai -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; $A shell sleep 10
  # Sempre volta à tela de Chat pelo menu lateral (o app reabre na última tela usada).
  toca 'content-desc="Open drawer"'; $A shell sleep 2; $A shell input tap 252 190; $A shell sleep 2
  $A shell input tap 1014 220; $A shell sleep 2
  # A conversa nova precisa estar vazia; senão a leitura do fim seria a de uma rodada anterior.
  UI | grep -q 'tokens/sec' && { echo "INVÁLIDA (a conversa nova não abriu)"; return; }
  toca 'content-desc="Message input"'; $A shell sleep 1
  $A shell input text "$PEDIDO"; $A shell sleep 2
  toca 'content-desc="Send"'; $A shell sleep 5; S=$(amostra); $A shell sleep $((ESPERA-5))
  # Rola a conversa até o fim (a linha da estatística pode ficar fora da área visível, e a árvore só mostra o visível).
  $A shell input swipe 600 1600 600 400 200; $A shell input swipe 600 1600 600 400 200; $A shell sleep 1
  X=$(UI); L=$(echo "$X" | grep -oE 'text="[^"]*tokens/sec[^"]*"' | head -1)
  W="~$(echo "$X" | grep -oE 'text="[^"]{40,}"' | grep -v tokens/sec | grep -v 'Write a 300' | sed 's/^text="//;s/"$//' | wc -w)"
  [ -z "$L" ] && { L="FALHOU"; $A exec-out screencap -p > "$DIR/falha-pocket-$(date +%H%M%S).png"; }
  echo "$L | ${W} palavras (≈1,3 token por palavra em inglês) | $S"
}
for r in $(seq 1 ${1:-5}); do
  esfria; echo "$2 rodada $r NOSSO  | cpu $(cpu) C | $(bat)| $(nosso)"
  esfria; echo "$2 rodada $r POCKET | cpu $(cpu) C | $(bat)| $(pocket)"
done
