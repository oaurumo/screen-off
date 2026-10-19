# ScreenOff
An android app to control your Android's screen to display or not display via Shizuku. This is not the same with pressing power key, since other APPs will CONTINUE working after turning off the screen.
# How it works
By reflecting "android.view.SurfaceControl" (the same way with Scrcpy). Check it in "SurfaceControl.java".

# Fix the adaptation issue of waking the screen when lifting the phone in portrait mode from Android 12 to Android 16
Fix the issue where lift-to-wake in portrait mode failed after turning off the screen via WLAN on Android 12–16, as well as after turning off the screen via ScreenOff on Android 16
