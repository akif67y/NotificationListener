# Notification Collector

An offline-only Android prototype that records selected Messenger and WhatsApp
notification fields for evaluating notification-based message acquisition.

## Privacy properties

- No `INTERNET` permission.
- Messenger is enabled by default; WhatsApp is opt-in.
- All other application packages are discarded immediately.
- Captures remain in the app-private SQLite database.
- Records older than seven days are deleted automatically.
- Captured message content is never written to Logcat.
- Android backup is disabled.

Notification-listener access is nevertheless powerful: Android can deliver the
service notifications from other apps before this app's package filter rejects
them. Only install builds made from source you trust.

## Build

Open the project in Android Studio, let Gradle sync, connect an Android phone with
USB debugging enabled, and select **Run**. After installation, open the app and
use **Open notification access settings** to grant access manually.

The app targets Android 15 (API 35) and supports Android 8.0 (API 26) or newer.

Build from a terminal with `./gradlew assembleDebug` (macOS/Linux) or
`gradlew.bat assembleDebug` (Windows). The debug APK is written to
`app/build/outputs/apk/debug/app-debug.apk`.
