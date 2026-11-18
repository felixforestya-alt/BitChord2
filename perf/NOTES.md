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

## Baseline, this pass (build 41945c31, test profile, window behind others but drawn as seen)

| State | CPU, cores | Frames/s | Drawing thread | UI thread | GPU % |
|---|---|---|---|---|---|
| Home, playing | 0.60 | 160 | 0.32 | 0.16 | 5.3 |
| Home scrolled (card become the bar) | 0.47 | 160 | 0.16 | 0.19 | 3.5 |
| Player open, playing | 0.68 | 160 | 0.24 | 0.10 (+0.2 in worker threads) | 7.4 |
| Library, playing | 0.42 | 160 | 0.18 | 0.13 | — |

- The drawing thread's time: about 60% submitting the frame to Direct3D (`Direct3DContextHandler.flush`), 15% presenting (`swap`), the rest replaying the recorded frame (`_nDrawPicture`). It scales with frames rendered and with what each frame holds.
- The UI thread spends over half its busy time in `AccessController.getStackAccessControlContext`: building a Swing event walks the whole stack. The nearest frame of ours is the write of `ambientTick` in `awaitAmbientFrame` — a write into the global snapshot wakes Compose's global snapshot manager, which posts a Swing event every frame.
- The window is rendered 160 times a second even before anything plays, while Compose's own draw of the root runs only about 18 times a second: something keeps the frame clock busy without changing anything. Not yet found.
- Scrolled Home stays at 160 frames/s: the card's visualiser and lyric sweep keep their frame loops after the card has faded into the bar.

## Findings after the first stop

- Paused Home still renders 160 frames/s in the test profile: it is not signed in, and the sign-in
  card's colour fade (a new palette every 3.6 s, faded over 3.6 s) steps on every frame. Hide that
  card (as Home is for a signed-in listener) and paused Home draws 103 frames/s.
- Stepping something slow less often helps even when the frame rate stays: a step re-records that
  layer's drawing, and the frame between steps replays it. The jam card's resting wave stepped
  every frame (about 0.1 px a step); stepped by quarter pixels it took the drawing thread from 0.35
  to 0.22 cores on paused Home with the frame rate unchanged.
- The snapshot wrap in `awaitAmbientFrame` (writes applied in the frame instead of through the
  global snapshot, to skip the Swing event and its stack walk) measured nothing: UI thread on Home
  0.12 against 0.10 cores without it, player 0.12 against 0.16, Library 0.07 against 0.05, total CPU
  within the noise. Reverted.
- The mesh-gradient drift on Home's service cards and the sign-in card, stepped every 33 ms
  instead of every frame: with the sign-in card on Home, 3201 against 3200 frames on paused Home
  (its colour fade asks for every frame anyway); without it, 3 pairs, 2050–2073 against 2074–2084
  frames in 20 s and 0.23–0.29 against 0.26–0.28 cores. Nothing on screen there drifts that way
  often enough to matter. Reverted.
- What is left on a paused Home without the sign-in card, about 103 frames/s: the jam card's
  resting wave, now 55–60 steps a second; the living backdrop, painted 20 times a second on a
  worker thread; short timers (activity watch, GPU keep-awake). The UI thread's busy time there is
  over half `AccessController.getStackAccessControlContext`, from Swing events being built.
- Noise: a single run's CPU moves by ±0.1 core (the same build measured 0.34 and 0.42 on paused
  Home). GPU share is steadier. Every decision here rests on at least three pairs run in turn, or on
  a frame count, which does not move with load.
- Android: no phone or emulator on this machine. What can be measured on the JVM is (analysis
  time, aurora frame cost, bytes a progress save rewrites); the rest is marked unmeasured.

## Attribution runs (all2 against leave-one-out builds), before the repeats

- == attr01-all2-home gpu%=4,3 | 11:58:34 home-playing     cpu=0,37 cores  frames=3201  playing=true focused=false
- == attr02-noB-home gpu%=5,0 | 11:59:30 home-playing     cpu=0,42 cores  frames=3202  playing=true focused=false
- == attr03-noA-home gpu%=4,1 | 12:00:25 home-playing     cpu=0,45 cores  frames=3199  playing=true focused=false
- == attr04-noD-home gpu%=4,8 | 12:01:22 home-playing     cpu=0,51 cores  frames=3095  playing=true focused=false
- == attr05-all2-home gpu%=5,8 | 12:02:19 home-playing     cpu=0,45 cores  frames=3202  playing=true focused=false
- == attr06-noB-home gpu%=9,9 | 12:03:14 home-playing     cpu=0,71 cores  frames=3138  playing=true focused=false
- == attr07-all2-scrolled gpu%=2,0 | 12:04:07 home-scrolled    cpu=0,25 cores  frames=1812  playing=true focused=false
- == attr08-noC-scrolled gpu%=9,6 | 12:05:03 home-scrolled    cpu=0,71 cores  frames=3200  playing=true focused=false
- == attr09-all2-page-LIBRARY gpu%=3,9 | 12:06:00 page-LIBRARY     cpu=0,28 cores  frames=1786  playing=true focused=false
- == attr10-noC-page-LIBRARY gpu%=6,9 | 12:06:57 page-LIBRARY     cpu=0,65 cores  frames=3197  playing=true focused=false
- == attr11-noA-page-LIBRARY gpu%=3,6 | 12:07:53 page-LIBRARY     cpu=0,29 cores  frames=1816  playing=true focused=false
- == attr12-all2-player gpu%=5,9 | 12:08:51 player-only      cpu=0,40 cores  frames=3198  playing=true focused=false
- == attr13-noA-player gpu%=6,0 | 12:09:47 player-only      cpu=0,34 cores  frames=3202  playing=true focused=false
