# Release 1.0 validation

- Build: Gradle 8.10.2, JDK 21.0.8, compile/target SDK 35, Build Tools 35.0.0.
- Debug and unsigned release APKs built successfully.
- All 126 JVM/Robolectric tests in 24 suites passed; no failures, errors or skips.
- Debug and release Android lint passed. Release lint reported 17 warnings and no errors.
- The public APK was aligned with `zipalign` and signed with a dedicated 4096-bit RSA release certificate. `apksigner verify --verbose --print-certs` and `zipalign -c 4` succeeded.
- APK metadata: application ID `dev.edgewave`, version name `1.0`, version code `27`, minimum SDK 29, target SDK 35; not debuggable; no native ABI-specific libraries.
- The signed release APK installed and launched successfully on an Android 15 / API 35 Pixel 6 emulator.
- Home, light/dark system themes, the Effects tab and the in-app Simplified Chinese selection were checked on the emulator. The crash log was empty after these checks.
- Four direct emulator screenshots are included in the README; see [SCREENSHOTS](SCREENSHOTS.md).

Release signing certificate SHA-256:

```text
0383b82b7cc591c54f10dfdad0c0f81f8b57822efd121f2eaaea0fd3e534a169
```

Audio-output capture and background overlay behavior depend on the physical device, Android version and player. The emulator UI checks do not establish music-analysis accuracy on every device. Pattern-based analysis remains experimental.
