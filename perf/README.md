# Measuring harnesses

Two builds of their own, outside the app's, used for the numbers in [PERF_REPORT.md](../PERF_REPORT.md).
Neither is part of the app or its build: the root `settings.gradle.kts` does not include them.

They exist because the machine the work was done on could not build the app itself — Google's
Maven (`dl.google.com`) and JitPack were out of its reach, and the app needs both — so the code
under measurement is compiled here straight from the source tree, with Maven Central alone.

## `harness-logic` — the audio thread, the preference store, the matcher

`sync.sh` copies the files under measurement in from the working tree, and the same files as they
are on `main` (or `BASE_REF`), renamed into package `baseline` with their classes prefixed
`Baseline`, so a change and what it replaced run in one JVM, after the same warm-up. It also copies
the app's own tests for those files, which run here unchanged.

```bash
perf/harness-logic/sync.sh
```

```bash
./gradlew -p perf/harness-logic test
```

Each benchmark prints `BENCH` lines: time per operation (median and best of 31 rounds, the two
sides alternating which goes first) and bytes allocated per operation on the calling thread
(`ThreadMXBean.getThreadAllocatedBytes`). Where a change is meant to leave the output alone, the
benchmark compares the two outputs sample for sample first and fails if they differ.

| Benchmark | What it runs |
|---|---|
| `AudioSpeedBench` | `DesktopAudioSpeed.process`, 1 s of stereo, 1024-frame blocks, 1.25x; outputs at 0.8x/1.25x/1.5x, 44.1/48 kHz compared |
| `VarispeedBench` | `DesktopVarispeed.process`, 1 s of 48 kHz stereo at 1.25x and 0.8x; outputs at four speeds compared |
| `EqualizerBench` | `DesktopEqualizer.process` per 1024-frame block, tone pad and a 7-band preset at rest; a whole session compared block for block |
| `AnalyzerBench` | `DesktopAudioAnalyzer.analyse` and `feed`; 400 analyses' bands and level compared |
| `PreferencesBench` | the calling thread's cost of a volume save, a track change, a like and a playlist edit, on a store the size of a used one |
| `MatcherBench` | `DesktopTrackMatcher.ranked` over 24 candidates; rankings compared |
| `LyricsCacheModel` | the lyrics cache's entries and heap over 72 simulated hours |
| `LyricSweepBench` | `LyricLine.revealedChars`, the per-frame sweep position (measured, not changed) |
| `StartupReadBench` | what the window's first composition reads and decodes (measured, not changed) |

## `harness-compose` — what recomposes, what asks for frames, what is drawn

Models of the changed composables: the same structure before and after, with the app's
dependencies (strings, palette, settings) replaced by fixed values. Rendered with
`ImageComposeScene` on Compose Multiplatform 1.5.12 — the newest that needs nothing from Google's
Maven — not the 1.10.3 the Windows app ships, so these count recompositions and frame requests and
compare pixels between before and after; they say nothing about how long the real window takes.

```bash
./gradlew -p perf/harness-compose test
```

`MODEL` lines give the counts; frames are compared as PNG and laid side by side in
`perf/harness-compose/build/frames/*.png`, before above, after below.
