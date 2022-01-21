<div align="center">
    <img alt="Pass Butler – Private Cloud Password Manager" src="./projectcover.jpg" width="600">
</div>

# Pass Butler for Android

This repository contains the source code of Pass Butler for Android.

## Installation

Install Pass Butler on the Google Play store.

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

    $ git clone ssh://git@github.com/passbutler/passbutler-android.git
    $ cd ./passbutler-android/

Clone submodules:

    $ git submodule update --init

### Manage log files

This is only possible for debug build, **not** for release build!

Pull log file:

    $ adb shell "run-as de.passbutler.app.debug cat /data/data/de.passbutler.app.debug/cache/passbutler-debug.log" > ~/Desktop/passbutler-debug.log

Remove log file:

    $ adb shell "run-as de.passbutler.app.debug rm /data/data/de.passbutler.app.debug/cache/passbutler-debug.log"

### Packaging

Build the Android Application Package (APK):

    $ ./gradlew assembleDebug

Install on a connected device:

    $ adb install ./PassButler/build/outputs/apk/debug/PassButler-debug.apk

## License

Pass Butler is licensed under the GNU Affero General Public License 3:

    Copyright (c) 2019-2022 Bastian Raschke

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License, version 3,
    as published by the Free Software Foundation.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program. If not, see <https://www.gnu.org/licenses/>.

The full license can be found in [`LICENSE.txt`](LICENSE.txt).
