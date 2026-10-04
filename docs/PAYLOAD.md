# DeadShot Bridge Payload Builder

The payload builder creates an import ZIP locally from a user's own compatible DeadShot data.

## Inputs

The current builder requires:

- the supported DeadShot `.jar`
- the supported DeadShot `.sp`

The `.jam` file is not required for the current payload build.

The builder validates the exact SHA-256 values documented in the repository before doing any conversion.

## Requirements

- Python 3
- Java 17 or newer
- Android SDK Platform 35
- Android SDK Build-Tools 35.0.0

## Windows

Double-click:

`RUN-PAYLOAD-BUILDER-WINDOWS.bat`

Follow the prompts for the JAR, SP, and Android SDK folder if it cannot be found automatically.

## Command line

```text
python tools/build_payload.py --jar PATH_TO_GAME.jar --sp PATH_TO_GAME.sp --sdk PATH_TO_ANDROID_SDK
```

The default output is:

`DeadShot_Data_for_DeadShot_Bridge_v0.1.3.zip`

## What the builder does

1. verifies the original JAR and SP hashes;
2. converts the three DeadShot Java game classes to `game.dex` with D8 from Build-Tools 35.0.0;
3. extracts the four MLD audio objects from the original scratchpad package;
4. converts those MLD objects to the exact verified MIDI files used by the bridge;
5. verifies every generated file;
6. creates a single import ZIP.

No game data is downloaded or uploaded.

## Do not publish the generated ZIP

The generated payload contains copyrighted game data derived from the user's copy. Keep it personal. Do not attach it to GitHub releases or commit it to the repository.
