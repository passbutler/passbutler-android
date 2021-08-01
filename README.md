# Pass Butler for Android

This repository contains the source code of Pass Butler for Android.

## Development setup

The following steps are tested with Ubuntu 20.04.

### Install Git

Git is needed for a build task that generates the version information for the build:

    $ sudo apt install git

### Install Android Studio

Recommended version:
- Android Studio 2020.3.1 Stable

Recommended plugin:
- SQLDelight (https://plugins.jetbrains.com/plugin/8191-sqldelight)

### Clone project

Clone repository:

    $ git clone ssh://git@git.sicherheitskritisch.de/passbutler/passbutler-android.git

Clone submodules:

    $ cd ./passbutler-android/
    $ git submodule update --init

### Packaging

Build the Android Application Package (APK):

    $ ./gradlew assembleDebug

Install on a connected device:

    $ adb install ./PassButler/build/outputs/apk/debug/PassButler-debug.apk
