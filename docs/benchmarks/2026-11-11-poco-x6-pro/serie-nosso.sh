#!/usr/bin/env bash
# Série só do nosso app, com as MESMAS regras do alternado2.sh (esfriar < 50 °C, espera fixa, uma leitura do logcat no fim).
# Uso: serie-nosso.sh <rodadas> <rótulo>
export MSYS_NO_PATHCONV=1
eval "$(sed -n '/^A=/,/^}$/p' "$(dirname "$0")/alternado2.sh" | sed -n '1,/^nosso() {/p' | sed '$d')"
eval "$(sed -n '/^nosso() {/,/^}$/p' "$(dirname "$0")/alternado2.sh")"
for r in $(seq 1 ${1:-3}); do esfria; echo "$2 rodada $r NOSSO | cpu $(cpu) C | $(bat)| $(nosso)"; done
