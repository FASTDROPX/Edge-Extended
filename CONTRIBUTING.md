# Contributing

Use JDK 17 and Android SDK 35. Build and run checks with:

```sh
./gradlew assembleDebug lintDebug testDebugUnitTest
```

Keep behavior changes focused and describe how they were verified. Check both themes and narrow layouts after UI changes. Default activation must remain off.

## Translations

The base `values/strings.xml` is English. Russian, Simplified Chinese, French and German are in `values-ru`, `values-zh`, `values-fr` and `values-de`. Add the same keys to all five files; keep formatting placeholders consistent. App languages are declared in `res/xml/locales_config.xml`.

## Music-analysis reports

Include the device, Android version, player, chosen mode/effects, and approximate timestamps of missed or false events. Explain what was expected. Distinguish a detection problem from an effect waiting for an occupied scene slot.

Do not commit copyrighted music, credentials, signing keys, generated builds or local SDK paths. Test audio is synthesized by fixtures under `app/src/test`; it is not shipped in the application.

## License

By contributing, you agree that your contribution is provided under the project's MIT license.
