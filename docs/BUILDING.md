# Building Edge Extended

## Requirements

- JDK 17 or 21.
- Android SDK Platform 35 and Build Tools 35.0.0.
- `ANDROID_HOME` pointing to the SDK, or an untracked `local.properties` with `sdk.dir`.

The Gradle wrapper downloads Gradle 8.10.2 and the project's dependencies on the first run.

```sh
./gradlew --no-daemon assembleDebug assembleRelease lintDebug lintRelease testDebugUnitTest
```

On Windows, use `gradlew.bat`. Debug builds use a local Android debug key. Release builds are deliberately unsigned:

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

## Signing your own release

Keep a dedicated signing key and its password outside the repository. Back them up securely: future updates need the same certificate. With Android Build Tools on your PATH, align and sign the unsigned APK:

```sh
zipalign -p -f 4 app/build/outputs/apk/release/app-release-unsigned.apk edge-aligned.apk
apksigner sign --ks /private/path/release.p12 --ks-key-alias edge-extended --out Edge-Extended-1.0.apk edge-aligned.apk
apksigner verify --verbose --print-certs Edge-Extended-1.0.apk
zipalign -c -v 4 Edge-Extended-1.0.apk
```

`apksigner` prompts for the keystore password. Never commit it or place a signing key in a release asset. Your own key cannot update the official APK or a development APK signed with a different key; Android requires a matching certificate.

The official 1.0 APK is a universal Java application (no native ABI-specific libraries), version name `1.0`, version code `27`, application ID `dev.edgewave`, minimum API 29 and target API 35. It uses a dedicated release certificate and is not debuggable.

## Automated checks

GitHub Actions builds debug and unsigned release variants, runs both lint variants and the JVM/Robolectric tests, and uploads test and lint reports. It does not have access to the private release signing key.
