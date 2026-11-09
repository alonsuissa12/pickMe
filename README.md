# Pick Me

**Pick Me** is a simple Android app for groups. Everyone puts one finger on the screen, and the app does the rest, it can:
- choose one person
- split everyone into groups
- give everyone a number.

No internet, no accounts, no permissions.

## Features

- **Choose one person:** one finger is picked at random and highlighted, and the others fade away.
- **Split into groups:** set the number of groups (2 to 10) and the app divides everyone randomly and as evenly as possible. Each group gets its own color and number.
- **Number everyone 1 to N:** every finger gets a random number from 1 to N.

## How to use

1. Open the app and choose a mode on the settings screen. In group mode, also set the number of groups.
2. Press **Start**.
3. Everyone puts one finger on the screen. Each finger gets a colored circle.
4. Hold still. After a 3-second countdown, the result appears and the phone vibrates. If someone adds or removes a finger, the countdown restarts.
5. The result stays on screen after you lift your fingers. Touch the screen again to start a new round, or tap **← Settings** to change the mode.

You need at least 2 fingers (or at least as many fingers as groups in group mode).

## Install

1. Go to the **Releases** page of this repository and download the latest `.apk` file.
2. Open the file on your Android phone.
3. If Android asks, allow your browser or file manager to **install unknown apps**.
4. If Google Play Protect shows a warning, tap **Install anyway**. This appears because the app is not distributed through the Play Store.

Requires Android 8.0 (API 26) or newer.

## Build from source

1. Install [Android Studio](https://developer.android.com/studio).
2. Open the project folder (the one containing `settings.gradle.kts`) and wait for the Gradle sync to finish.
3. Press **Run** to install on a connected phone or emulator, or build an APK with `gradle assembleDebug`.
   The APK is created at `app/build/outputs/apk/debug/app-debug.apk`.

Note: the Android emulator can only simulate two fingers (hold **Ctrl** and click). Use a real phone to test with more.

## Built with

- Kotlin
- Jetpack Compose (Material 3)

## Credits

Created by Alon Suissa.

