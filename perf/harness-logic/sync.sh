#!/usr/bin/env bash
# Copies the files under measurement into this harness: as they are in the working tree, and as
# they are on ${BASE_REF:-main}, renamed into package `baseline` with their names prefixed
# `Baseline`, so the two run side by side. Also copies the app's own tests for those files.
set -euo pipefail
H=$(cd "$(dirname "$0")" && pwd)
REPO=$(cd "$H/../.." && pwd)
rm -rf "$H/src/main/kotlin/repo" "$H/src/main/kotlin/baseline" "$H/src/test/kotlin/repo"
mkdir -p "$H/src/main/kotlin/repo" "$H/src/main/kotlin/baseline" "$H/src/test/kotlin/repo"
D=desktopApp/src/main/kotlin/com/music/bitchord/desktop
P=shared/src/commonMain/kotlin/com/music/bitchord/playback
T=desktopApp/src/test/kotlin/com/music/bitchord/desktop
MAIN_FILES="$D/DesktopPreferenceWriter.kt $D/DesktopPreferenceChunks.kt $D/DesktopAudioSpeed.kt $D/DesktopVarispeed.kt
  $D/DesktopTempoBuffer.kt $D/DesktopEqualizer.kt $D/DesktopAudioAnalyzer.kt $D/DesktopTrackMatcher.kt
  $P/EqualizerCurve.kt $P/EqualizerPreset.kt $P/PeakLimiter.kt
  shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/lyrics/LyricLine.kt"
for f in $MAIN_FILES; do
  [ -f "$REPO/$f" ] && cp "$REPO/$f" "$H/src/main/kotlin/repo/"
done
for spec in DesktopTrackMatcher DesktopAudioSpeed DesktopVarispeed DesktopEqualizer DesktopAudioAnalyzer DesktopPreferenceChunks; do
  git -C "$REPO" show "${BASE_REF:-main}:$D/$spec.kt" \
    | sed -e "s/^package com.music.bitchord.desktop/package baseline\nimport com.music.bitchord.playback.*/" \
          -e "s/\b$spec\b/Baseline$spec/g" > "$H/src/main/kotlin/baseline/$spec.kt"
done
for t in DesktopMatcherTest DesktopPreferenceWriterTest DesktopPreferenceChunksTest DesktopAudioSpeedTest \
         DesktopVarispeedTest DesktopTempoBufferTest DesktopEqualizerTest; do
  [ -f "$REPO/$T/$t.kt" ] && cp "$REPO/$T/$t.kt" "$H/src/test/kotlin/repo/"
done
echo "synced from $REPO (baseline: ${BASE_REF:-main})"
