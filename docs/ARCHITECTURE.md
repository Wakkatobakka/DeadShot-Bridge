# Architecture

DeadShot Bridge separates the redistributable Android host from user-supplied game data.

## Public APK

The APK contains:

- the Android bridge shell;
- independently written DoJa compatibility interfaces/runtime code used by DeadShot;
- rendering, input, scratchpad, audio, pause/resume, reporting, and diagnostics glue;
- the DeadShot-specific custom control deck;
- payload import and integrity verification code.

It does not contain the original DeadShot game classes, scratchpad package, or converted game audio.

## Imported payload

The local payload ZIP contains `game.dex`, `deadshot.sp`, and four verified MIDI files. On import, DeadShot Bridge verifies exact sizes and SHA-256 values, then copies the required files to application-private internal storage.

The original game classes are loaded from the verified app-private `game.dex` at runtime with Android's `DexClassLoader`. The importer opens the DEX output, marks the file read-only before writing its bytes, and re-applies the read-only state immediately before class loading. This follows the modern Android requirement that dynamically loaded code not remain writable. The bridge's DoJa compatibility classes remain in the installed APK and act as the parent runtime for those original game classes.

Scratchpad initialization and audio playback read only from the verified app-private payload. Existing app-private save data remains separate from the imported seed data.

## Gameplay boundary

The v0.1.3 public-data architecture is intended to change distribution, not DeadShot gameplay. The accepted v0.1.2 renderer, native input numbers, custom movement/action behavior, contact routing, pause/resume policy, and game logic remain the baseline after the payload is loaded.
