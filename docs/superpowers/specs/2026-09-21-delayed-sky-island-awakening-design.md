# Delayed Sky-Island Awakening Design

## Goal

Move the first sky-island awakening presentation away from the instant-login moment. A new player should receive the cinematic awakening after two minutes of actual online play, with a dramatic sound cue, while preserving reliable coordinate delivery and the existing one-time-per-manifest behavior.

## Player experience

- The first eligible player login starts a 2,400-tick online countdown.
- Logging out pauses the countdown; logging back in resumes it.
- When the countdown reaches zero, the center-screen title remains `天空岛向你投来注视`, with subtitle `远方的浮岛正在苏醒`.
- The title is accompanied by layered vanilla sounds: a low portal/ambient cue followed by a stronger impact cue. No additional audio asset is required.
- The awakening is shown once per player for the current sky-island manifest version.
- Returning players who have already seen it do not receive it again.

## Ordering with island completion

Sky-island construction may finish before or after the two-minute timer.

- If construction is still running at two minutes, show the awakening immediately. The existing completion title and coordinate reminder appear when construction later completes.
- If construction finishes before two minutes, hold the first completion title and coordinate reminder. At two minutes, show the awakening, then show the completion title and coordinates after an eight-second presentation gap.
- Login reminders for players who have already completed the full first-run presentation continue to appear in chat as before.
- Actionable generation errors remain immediate and are never delayed by the presentation timer.

## State and runtime behavior

Persist per-player, per-manifest notification state in `PlayerPersisted`:

- remaining online ticks before awakening;
- whether the awakening was shown;
- whether completion presentation is waiting behind awakening;
- remaining ticks in the post-awakening presentation gap.

The server tick handler advances notification timers only for connected server players. State is written at meaningful transitions and periodically while counting down so a crash or disconnect cannot reset the full sequence. Manifest-version changes start a fresh presentation without disturbing unrelated player data.

## Sound behavior

Use existing Minecraft sound events through server-to-player playback. This keeps the effect available to every teammate who has the TNC JAR and avoids adding another copyrighted or separately distributed sound file. The sounds use the master/ambient channel rather than the travel-music queue, so they do not become playlist entries.

## Music distribution

The committed `modpack/.../mods/tnc-1.0.0.jar` already contains `assets/tnc/sounds.json` and all fourteen converted travel tracks. A teammate who pulls the repository and launches the supplied modpack receives the complete playback feature without a separate music archive.

Raw source audio remains intentionally ignored. A separate archive is required only for someone who needs to regenerate or replace tracks and rebuild the JAR locally. Normal collaboration should distribute refreshed music through the rebuilt committed TNC JAR.

## Verification

- Unit-test countdown decisions and both completion-order branches.
- Verify logout/relogin resumes rather than restarts the timer.
- Verify awakening and completion are each emitted once per manifest.
- Verify errors remain immediate.
- Run the complete Java 17 test and build suite.
- Confirm the rebuilt JAR contains `sounds.json`, all travel `.ogg` files, and is byte-identical in the repository modpack and active PCL instance.
