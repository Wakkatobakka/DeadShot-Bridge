# DeadShot Bridge v0.1.3

v0.1.3 is the first public-safe DeadShot Bridge release.

## Player flow

1. Install the bridge APK.
2. Build a personal payload locally from a compatible DeadShot JAR/SP.
3. Import that payload into DeadShot Bridge.
4. Play.

No original DeadShot game payload is included in the public APK or repository.

## Public-distribution changes from the accepted v0.1.2 gameplay build

- Removed the original DeadShot JAR/JAM/SP, converted music, and original game classes from the bridge APK.
- Added local user-payload import through Android's document picker.
- Added exact size/SHA-256 verification for `game.dex`, `deadshot.sp`, and all four converted MIDI tracks.
- Added app-private dynamic loading for the verified game DEX, with the imported DEX kept read-only for modern Android dynamic-code-loading requirements.
- Scratchpad seed and music now read from the imported app-private payload.
- Added the DeadShot Payload Builder for creating the import ZIP locally from the user's compatible JAR/SP files.
- Added Import/Replace DeadShot Data to the launcher.

## Intentionally unchanged from the accepted gameplay baseline

- game rendering;
- native DeadShot key numbers;
- four-way/eight-way movement behavior;
- the bespoke DeadShot custom control deck;
- full phone keypad fallback;
- touch contact routing;
- pause/resume policy;
- reporting/diagnostics behavior;
- save/scratchpad semantics after initialization.

## Device validation

The v0.1.3 release-candidate import -> boot -> gameplay path was owner-tested on a Samsung Galaxy S25 Ultra running Android 16 and reported fully working. The final v0.1.3 build changes only release metadata/status text from that tested RC; the importer and gameplay logic are unchanged.
