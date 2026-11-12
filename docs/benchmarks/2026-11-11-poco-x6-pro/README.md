# Speed comparison on a POCO X6 Pro (November 11, 2026)

These are the raw numbers behind the comparison in the [v0.2.4 notes](../../../CHANGELOG.md). Every run is here,
including the ones I left out, and the scripts that produced them.

## Setup

- Phone: POCO X6 Pro (MediaTek Dimensity 8300: 4 fast and 4 efficiency cores), connected by USB (`USB powered: true`).
- Apps, each with its default settings: On-Device LLM v0.2.4 (staging build), PocketPal AI 1.23.0 and Google AI Edge
  Gallery 1.0.23, installed from their official GitHub releases (SHA-256 checked).
- Prompt: "Write a 300 word story about a robot who learns to paint", in a new chat each time.
- Before each run, the CPU cooled below 50 °C (`dumpsys thermalservice`). The apps took turns, except the Gallery: entering
  its chat reloads the model, so it ran its own series under the same cooling rule.
- Nothing read the screen during a generation. Each run waited a fixed time (the same for both apps in a pair) and was read
  once at the end: this app from its log line, PocketPal from the line under the answer, the Gallery from its stats panel.
- Five seconds after sending, both apps got the same light sample: the thermal status (always 0) and the current
  frequency of the fast cores.

## Results (medians of the valid runs)

| Model | App | Generation speed | Runs |
|---|---|---|---|
| Qwen2.5-0.5B-Instruct Q4_K_M (same file) | On-Device LLM v0.2.4 | 35.4 tokens/s | 12 |
| | PocketPal AI 1.23.0, default (6 threads) | 17.7 tokens/s | 5 |
| | PocketPal AI 1.23.0, set by hand to 4 threads | 36.5 tokens/s | 3 |
| Qwen2.5-1.5B-Instruct (each app's own build) | On-Device LLM v0.2.4, GGUF Q4_K_M 1.1 GB, CPU | 15.2 tokens/s | 3 |
| | Google AI Edge Gallery 1.0.23, LiteRT 1.6 GB, GPU (default) | 8.5 tokens/s | 3 |
| | Google AI Edge Gallery 1.0.23, LiteRT 1.6 GB, CPU | 5.9 tokens/s | 3 |

How each app counts speed:
- This app: wall-clock time from the end of the prompt to the last token.
- PocketPal: `timings.predicted_per_second` from llama.rn, measured inside the native token loop. It covers less work
  than this app's clock, so the difference in method favors PocketPal.
- The Gallery: the decode speed in its stats panel.

The answers had different lengths. This app stopped at 238 tokens every time (0.5B), while PocketPal wrote roughly 260 to
360 words, and the Gallery wrote 421 tokens on the GPU and 479 on the CPU (1.5B).

## Runs left out

The files below cover the PocketPal runs that gave no usable reading:

- `alternado2-fabrica-6t.txt`, round 2: it showed the numbers from the previous chat (19.55 tokens/s, 1203 ms, identical
  to round 1), because the new chat had not opened.
- `alternado2-fabrica-6t.txt`, round 3: the model was repeating itself and was still writing when the reading was taken
  (`screens/pocketpal-excluded-194144.png`).
- `alternado2-ajustado-4t.txt`, rounds 3 and 5: the model repeated itself until its 2,048-token context ran out
  (`screens/pocketpal-excluded-213813.png` and `-215117.png`).

`discarded/` has the earlier series that I threw away entirely, for both apps, because their method was flawed:

- the first two series read PocketPal's screen every few seconds during generation;
- one series ran while PocketPal sat on its settings screen;
- one 4-thread series had only one valid PocketPal reading;
- one 1.5B series ran without cooling between rounds.

## Files

| File | What it has |
|---|---|
| `alternado2.sh` | This app and PocketPal, alternating (set `SERIAL` to your phone's adb serial) |
| `serie-gallery.sh`, `serie-nosso.sh` | The Gallery series and this app's matching series |
| `alternado2-fabrica-6t.txt`, `alternado2-fabrica-6t-extra.txt` | This app against PocketPal at its default |
| `alternado2-ajustado-4t.txt` | This app against PocketPal set to 4 threads |
| `serie-gallery-gpu.txt`, `serie-gallery-cpu.txt`, `serie-nosso-1.5b.txt` | The 1.5B runs |
| `screens/` | Each Gallery stats panel, the settings of each configuration and the excluded PocketPal runs (cropped) |

PocketPal AI and Google AI Edge Gallery are the names of their respective owners' products. I'm not affiliated with either
project, and both are good open-source apps.
