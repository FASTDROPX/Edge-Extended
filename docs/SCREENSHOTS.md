# Screenshots

The README gallery contains direct Android emulator captures of the signed **Edge Extended 1.0** release APK, not mockups or test-rendered layouts.

| File | Screen |
|---|---|
| `images/home-light.png` | Home, English, light system theme |
| `images/home-dark.png` | Home, English, dark system theme |
| `images/effects-light.png` | Effects, English, light theme |
| `images/effects-chinese.png` | Effects, Simplified Chinese selected from the in-app language menu |

Environment: Pixel 6 Android Virtual Device, Android 15 / API 35, Google APIs x86_64, 1080 × 2400 pixels, density 420 dpi. Software GPU rendering was used. The app package was `dev.edgewave`, version `1.0` / code `27`, installed from the signed release APK.

All images were saved with `adb shell screencap -p` and transferred with `adb pull`, without compositing or retouching. Android System UI demo mode set the clock to 10:00 and hid notifications. Application settings and text were not altered for the images; visualization and effect switches remained off.

To reproduce, install a signed release build, open Home, select light/dark system theme, then open Effects. Choose Simplified Chinese from the top-right language menu for the localized capture.
