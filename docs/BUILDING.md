# Building DeadShot Bridge

DeadShot Bridge builds offline without Gradle dependency resolution.

## Requirements

- Java 17 or newer
- Python 3
- Android SDK Platform 35 (`platforms/android-35/android.jar`)
- Android SDK Build-Tools 35.0.0

Set:

- `ANDROID_JAR` to the Platform 35 `android.jar`
- `ANDROID_BUILD_TOOLS` to the host Build-Tools `35.0.0` directory

Then run:

```text
python3 tools/build.py
python3 tools/check.py
```

The finished APK is written to `dist/`.

## Signing

The public repository intentionally contains no official private signing key.

On the first local build, `tools/build.py` creates a new private signing identity in `signing/`. Keep that folder private if you want future local APKs to update over one another.

Official release APKs use a separate private project signing identity that is never committed. Therefore a locally signed APK will not update over the official release APK without uninstalling the official build first.

## Game data

Game data is not required to compile the bridge APK and is never embedded into the public APK. DeadShot game data is imported separately at runtime from a locally generated payload ZIP.
