# Pass Butler for Android

This repository contains the source code of Pass Butler for Android.

## Development setup

The following steps are tested with Ubuntu 20.04.

### Install Android Studio

Recommended version:
- Android Studio 4.1.2 Stable

Recommended plugin:
- SQLDelight (https://plugins.jetbrains.com/plugin/8191-sqldelight)

### Packaging

Build the Android Application Package (APK):

    $ ./gradlew assembleDebug

Install on a connected device:

    $ adb install ./PassButler/build/outputs/apk/debug/PassButler-debug.apk
