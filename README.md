# ScreenOff
An android app to control your Android's screen to display or not display via Shizuku. This is not the same with pressing power key, since other APPs will CONTINUE working after turning off the screen.
# How it works
By reflecting "android.view.SurfaceControl" (the same way with Scrcpy). Check it in "SurfaceControl.java".
forked from https://github.com/WuDi-ZhanShen/ScreenOff and fixed.

# Fix the adaptation issue
Fix the issue where lift-to-wake in portrait mode failed after turning off the screen via WLAN on Android 12–16, as well as after turning off the screen via ScreenOff on Android 16
