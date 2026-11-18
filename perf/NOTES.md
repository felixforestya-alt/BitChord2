# Notes from the second performance pass

One finding to a line: what turned out true, what was a false lead, and why. Read at the start of
work and after every context compaction. Numbers and the accepted changes are in
[PERF_REPORT.md](../PERF_REPORT.md).

## How this pass measures

- This pass runs on the author's own PC (Windows, RTX 5070 Ti, 4K 160 Hz), so the real app is
  built and measured, not models of it.
- `BITCHORD_PROFILE=1 BITCHORD_PROFILE_LOAD=1` plus one of `BITCHORD_PROFILE_LOAD_HOME=1`,
  `BITCHORD_PROFILE_LOAD_PLAYER=1`, `BITCHORD_PROFILE_LOAD_PAGE=LIBRARY` gives one state in about
  a minute: `load <state> cpu=… cores frames=…`, each thread's share, then
  `profile <state>-even …` with the frame gaps and the drawing thread's time per frame.
- A test window that is not the foreground window and lies inside the foreground window's rectangle
  counts as covered and stops drawing: a run launched from a script measured 422 frames in 24 s
  instead of 3800. Every measuring run sets `BITCHORD_NEVER_QUIET=1`.
- Environment variables set for one run stay set in the same PowerShell session: a "home" run after
  a "player" run measured the player. The run script clears `BITCHORD_*` first.
- `stuck=` in the stress lines is how long the reported playhead stood still. The playhead is
  reported about four times a second, so 220–280 ms there is the reporting cadence, not a stall.
- The `frames: … late, worst …` line counts every gap over two refreshes while sound plays. A
  window that is covered, or on a page where only the seek bar moves, draws a few frames a second
  by design, and all of them counted as late.

## What the window is made of, per frame

- Compose 1.10.3 on skiko 0.9.37 keeps each graphics layer as a native render node; only a layer
  that was invalidated is recorded again. The whole window is still rasterised on the GPU every
  frame that anything moves — Skia has no partial redraw — so what a still part of the window
  costs per frame is its share of that rasterising.
- The decoder's thread is 0.01 of a core and the audio thread 0.00–0.01. Their copies and
  allocations (`DesktopDecodeAhead`, `DesktopAudioDecoder.readSamples`) are not worth changing.
- The home card's liquid glass is drawn under the card's own background, which is opaque while the
  card rests at the top of Home: blur, lens and rim all painted over.
