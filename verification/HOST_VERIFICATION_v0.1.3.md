# DeadShot Bridge v0.1.3 host verification

The final public release passed the host checks available in the build environment:

- 17 existing behavioral helper assertions;
- Android manifest/package/version checks;
- Android `versionCode` 5 / `versionName` 0.1.3;
- no Android permissions requested;
- no original DeadShot JAR/JAM/SP, converted MIDI payload, or original game class definitions embedded in the APK;
- no original game payload present in the public source tree;
- accepted DeadShot custom-control geometry and native `#`/`OK` selection logic retained;
- payload importer present and configured for app-private storage, with `game.dex` made read-only before dynamic loading;
- exact expected hashes present for imported game DEX/SP/MIDI content;
- official final APK signed with the same certificate identity as the phone-tested v0.1.3-rc1 build.

The separate phone-validation record documents the owner device pass. Host verification does not independently reproduce physical touch feel or Android device behavior.
