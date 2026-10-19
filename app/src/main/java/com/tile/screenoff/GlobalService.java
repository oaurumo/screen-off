package com.tile.screenoff;

import static java.lang.Math.abs;

import android.accessibilityservice.AccessibilityService;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class GlobalService extends AccessibilityService implements SharedPreferences.OnSharedPreferenceChangeListener {


    private WindowManager windowManager;
    private WindowManager.LayoutParams params;
    private ImageView view;
    SharedPreferences sp;
    private boolean exist = false, canmove, doubleTap, shake, volume, netControl;
    int size, sensity, scrOnKey, scrOffKey;
    private int SCREEN_WIDTH, SCREEN_HEIGHT;
    OrientationEventListener listener;

    private boolean isServiceOK = false;
    IScreenOff iScreenOff = null;

    // PARTIAL_WAKE_LOCK：息屏时保持 CPU 和传感器运行，确保方向监听器在 Android 16 上仍能工作
    private PowerManager.WakeLock partialWakeLock;
    // WifiLock：息屏时保持 WiFi 活跃，确保局域网远程控制能正常响应
    private WifiManager.WifiLock wifiLock;
    // 主线程 Handler：HTTP 请求在后台线程触发，需切回主线程操作 UI/监听器/唤醒锁
    private Handler mainHandler;
    // 标记屏幕是否由本应用主动关闭（用于区分系统息屏和应用息屏）
    private boolean screenOffByApp = false;

    // ===== 诊断日志：同时输出到 logcat 和文件（/sdcard/Android/data/com.tile.screenoff/files/service.log）=====
    private void diag(String msg) {
        Log.e("GlobalService", msg);
        try {
            java.io.File dir = getExternalFilesDir(null);
            if (dir == null) return;
            java.io.File f = new java.io.File(dir, "service.log");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(f, true);
            String line = new java.text.SimpleDateFormat("MM-dd HH:mm:ss.SSS", java.util.Locale.US)
                    .format(new java.util.Date()) + " " + msg + "\n";
            fos.write(line.getBytes());
            fos.close();
        } catch (Exception ignored) {
        }
    }

    final BroadcastReceiver myReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case "intent.screenoff.sendBinder":
                    BinderContainer binderContainer = intent.getParcelableExtra("binder");
                    IBinder binder = binderContainer.getBinder();
                    //如果binder已经失去活性了，则不再继续解析
                    if (!binder.pingBinder()) break;
                    isServiceOK = true;
                    iScreenOff = IScreenOff.Stub.asInterface(binder);
                    diag("binder received, service connected, SDK=" + Build.VERSION.SDK_INT);
                    floatWindow();
                    break;
                case Intent.ACTION_SCREEN_OFF:
                    diag("ACTION_SCREEN_OFF received, screenOffByApp=" + screenOffByApp);
                    // 如果屏幕是由本应用主动关闭的，则不处理此广播
                    // 避免在 Android 16 上 setDisplayPowerMode(POWER_MODE_OFF) 触发 ACTION_SCREEN_OFF 后
                    // 禁用方向监听器和将状态设为 STATE_OFF，导致无法通过竖直手机点亮屏幕
                    if (screenOffByApp) {
                        break;
                    }
                    try {
                        if (isServiceOK) iScreenOff.updateNowScreenState(false);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    view.setKeepScreenOn(false);
                    listener.disable();
                    if (exist) windowManager.updateViewLayout(view, params);
                    break;
                case Intent.ACTION_SCREEN_ON:
                case Intent.ACTION_USER_PRESENT:
                    diag(intent.getAction() + " received");
                    // 屏幕亮起时重置标志
                    screenOffByApp = false;
                    try {
                        if (isServiceOK) iScreenOff.updateNowScreenState(true);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    break;
                case "action.ScrOff":
                    screenoff(intent.getBooleanExtra("state", true));
                    break;
                case "intent.screenoff.exit":
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        disableSelf();
                    } else {
                        stopSelf();
                    }
                    break;
            }

        }
    };

    @Override
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
    }

    @Override
    public void onInterrupt() {

    }


    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        if (s.startsWith("x") || s.startsWith("y")) return;

        view.setVisibility(getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT && sharedPreferences.getBoolean("land", false) ? View.GONE : View.VISIBLE);
        size = (int) (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, sharedPreferences.getInt("size", 50), getResources().getDisplayMetrics()));
        params.height = size;
        params.width = size;
        params.alpha = sharedPreferences.getInt("tran", 90) * 0.01f;
        canmove = sharedPreferences.getBoolean("canmove", true);
        doubleTap = sharedPreferences.getBoolean("doubleTap", false);
        shake = sharedPreferences.getBoolean("shake", false);
        sensity = sharedPreferences.getInt("sensity", 10);
        volume = sharedPreferences.getBoolean("volume", false);
        scrOnKey = sharedPreferences.getInt("scrOnKey", 24);
        scrOffKey = sharedPreferences.getInt("scrOffKey", 25);
        netControl = sharedPreferences.getBoolean("net", false);
        if (netControl) startServer();
        floatWindow();

    }


    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        mainHandler = new Handler(Looper.getMainLooper());
        sp = getSharedPreferences("s", 0);


        sensity = sp.getInt("sensity", 10);
        listener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) return;
                boolean wake = ((orientation >= 360 - sensity || orientation <= sensity) || orientation >= 90 - sensity && orientation <= 90 + sensity) || orientation >= 180 - sensity && orientation <= 180 + sensity || orientation >= 270 - sensity && orientation <= 270 + sensity;
                //下面是手机旋转准确角度与四个方向角度（0 90 180 270）的转换
                diag("onOrientationChanged orientation=" + orientation + " wake=" + wake);
                if (wake) {
                    screenoff(false);
                    this.disable();
                    diag("orientation listener disabled after wake");
                }
            }

        };
        windowManager = (WindowManager) getSystemService(Service.WINDOW_SERVICE);
        GetWidthHeight();
        size = (int) (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, sp.getInt("size", 50), getResources().getDisplayMetrics()));

        params = new WindowManager.LayoutParams(size, size, Build.VERSION.SDK_INT >= 22 ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY : WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH, PixelFormat.RGBA_8888);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        params.alpha = sp.getInt("tran", 90) * 0.01f;
        boolean isLand = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        params.x = sp.getInt("x" + (isLand ? "1" : "2"), 0);
        params.y = sp.getInt("y" + (isLand ? "1" : "2"), 0);
        view = new ImageView(this);
        ShapeDrawable oval = new ShapeDrawable(new OvalShape());
        oval.getPaint().setColor(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? getColor(R.color.bg) : 0xffc1e8ff);
        view.setBackground(oval);
        view.setImageResource(R.drawable.fw);
        canmove = sp.getBoolean("canmove", true);
        doubleTap = sp.getBoolean("doubleTap", false);
        shake = sp.getBoolean("shake", false);
        volume = sp.getBoolean("volume", false);
        scrOnKey = sp.getInt("scrOnKey", 24);
        scrOffKey = sp.getInt("scrOffKey", 25);
        netControl = sp.getBoolean("net", false);
        if (netControl) startServer();
        view.setOnTouchListener(new View.OnTouchListener() {
            int lastX = 0;
            int lastY = 0;
            int paramX = 0;
            int paramY = 0;
            long lastDown = 0, lastUp = 0;
            boolean moved = false;

            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {

                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        lastX = (int) motionEvent.getRawX();
                        lastY = (int) motionEvent.getRawY();
                        paramX = params.x;
                        paramY = params.y;
                        params.alpha = 1;
                        new Handler().postDelayed(() -> {
                            if (System.currentTimeMillis() - lastUp >= 400 && !moved) {
                                screenoff(true);
                            }
                        }, 400);
                        break;
                    case MotionEvent.ACTION_MOVE:

                        int dx = (int) motionEvent.getRawX() - lastX;
                        int dy = (int) motionEvent.getRawY() - lastY;
                        if (abs(dx) > 4 || abs(dy) > 4)
                            moved = true;
                        if (!canmove) return true;
                        params.x = paramX + dx;
                        params.y = paramY + dy;
                        windowManager.updateViewLayout(view, params);

                        break;
                    case MotionEvent.ACTION_UP:
                        lastUp = System.currentTimeMillis();
                        params.alpha = sp.getInt("tran", 90) * 0.01f;
                        params.x = (params.x > (SCREEN_WIDTH - size) * 0.43) ? (SCREEN_WIDTH - size) / 2 : ((params.x < (SCREEN_WIDTH - size) * -0.43) ? -(SCREEN_WIDTH - size) / 2 : params.x);
                        params.y = Math.min(Math.max(params.y, -(SCREEN_HEIGHT - size) / 2), (SCREEN_HEIGHT - size) / 2);
                        windowManager.updateViewLayout(view, params);
                        moved = false;
                        boolean isLand = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
                        sp.edit().putInt("x" + (isLand ? "1" : "2"), params.x).putInt("y" + (isLand ? "1" : "2"), params.y).apply();
                }


                if (!doubleTap) return false;
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_OUTSIDE:
                        if (System.currentTimeMillis() - lastDown <= 400)
                            screenoff(false);
                        lastDown = System.currentTimeMillis();
                        break;
                }
                return false;
            }
        });
        view.setVisibility(getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT && sp.getBoolean("land", false) ? View.GONE : View.VISIBLE);
        floatWindow();


        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction("action.ScrOff");
        filter.addAction("intent.screenoff.sendBinder");
        filter.addAction("intent.screenoff.exit");
        registerReceiver(myReceiver, filter);
        sp.registerOnSharedPreferenceChangeListener(this);
    }


    void screenoff(Boolean bb) {
        diag("screenoff(" + bb + ") called, isServiceOK=" + isServiceOK + ", shake=" + shake);
        if (!isServiceOK) return;
        try {
            // bb=true 表示息屏：仅在屏幕已亮时才息屏（避免重复息屏）
            // bb=false 表示亮屏：始终允许点亮（修复 STATE_OFF 状态下无法点亮的问题）
            if (bb && iScreenOff.getNowScreenState() == 0) {
                diag("screenoff(true) skipped, already off");
                return;
            }
            if (bb) {
                // 先持有 PARTIAL_WAKE_LOCK + WifiLock 再息屏，避免息屏与拿锁之间的间隙设备进入休眠
                // （Android 16 上间隙期休眠会导致方向传感器停止工作、WiFi 挂起，无法再通过竖直手机或局域网点亮）
                acquirePartialWakeLock();
                acquireWifiLock();
                diag("partial wake lock & wifi lock acquired");
            }
            iScreenOff.setPowerMode(bb);
            diag("setPowerMode(" + bb + ") returned, nowState=" + iScreenOff.getNowScreenState());
            // 息屏时不要设置 KEEP_SCREEN_ON：在 Android 12 等系统上该标志会让系统立即发出
            // 虚假的 ACTION_SCREEN_ON，重置 screenOffByApp 并把状态改成 STATE_ON，
            // 导致竖起手机时 setPowerMode(false) 被忽略、屏幕无法点亮。
            // 进程/传感器保活由 PARTIAL_WAKE_LOCK 负责，无需 KEEP_SCREEN_ON。
            view.setKeepScreenOn(false);
            if (bb) {
                // 应用主动息屏：设置标志位，避免 ACTION_SCREEN_OFF 广播禁用方向监听器
                screenOffByApp = true;
                if (shake) {
                    listener.enable();
                    diag("orientation listener enabled");
                } else {
                    diag("shake preference is OFF, orientation listener NOT enabled");
                }
            } else {
                // 点亮屏幕：清除标志位并释放唤醒锁/WiFi锁
                screenOffByApp = false;
                releasePartialWakeLock();
                releaseWifiLock();
            }
        } catch (RemoteException e) {
            diag("screenoff RemoteException: " + e);
            e.printStackTrace();
        }

    }

    /**
     * 获取 PARTIAL_WAKE_LOCK，保持 CPU 运行（不保持屏幕亮）
     * 用于在息屏状态下仍能接收方向传感器事件
     */
    private void acquirePartialWakeLock() {
        if (partialWakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                partialWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ScreenOff::OrientationWakeLock");
                partialWakeLock.setReferenceCounted(false);
            }
        }
        if (partialWakeLock != null && !partialWakeLock.isHeld()) {
            partialWakeLock.acquire();
        }
    }

    /**
     * 释放 PARTIAL_WAKE_LOCK
     */
    private void releasePartialWakeLock() {
        if (partialWakeLock != null && partialWakeLock.isHeld()) {
            partialWakeLock.release();
        }
    }

    /**
     * 获取 WifiLock，息屏时保持 WiFi 活跃（避免息屏后局域网控制无响应）
     */
    private void acquireWifiLock() {
        if (wifiLock == null) {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "ScreenOff::WifiLock");
                wifiLock.setReferenceCounted(false);
            }
        }
        if (wifiLock != null && !wifiLock.isHeld()) {
            try {
                wifiLock.acquire();
            } catch (Exception e) {
                diag("acquireWifiLock failed: " + e);
            }
        }
    }

    /**
     * 释放 WifiLock
     */
    private void releaseWifiLock() {
        if (wifiLock != null && wifiLock.isHeld()) {
            try {
                wifiLock.release();
            } catch (Exception e) {
                diag("releaseWifiLock failed: " + e);
            }
        }
    }


    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (!volume || !isServiceOK || event.getAction() == KeyEvent.ACTION_UP)
            return super.onKeyEvent(event);

        try {
            final int keycode = event.getKeyCode();
            final int nowState = iScreenOff.getNowScreenState();
            if (keycode == scrOffKey && nowState == 1) {
                screenoff(true);
                return true;
            }
            if (keycode == scrOnKey && nowState == 2) {
                screenoff(false);
                return true;
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        return super.onKeyEvent(event);
    }

    public void floatWindow() {
        if (!isServiceOK) return;
        if (sp.getBoolean("float", true)) {
            if (!exist) {
                windowManager.addView(view, params);
                exist = true;
            } else {
                windowManager.updateViewLayout(view, params);
            }
        } else {
            if (view != null) {
                try {
                    windowManager.removeViewImmediate(view);
                } catch (Exception ignored) {
                }
                exist = false;
            }
        }
    }


    void GetWidthHeight() {
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        SCREEN_WIDTH = metrics.widthPixels;
        SCREEN_HEIGHT = metrics.heightPixels;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        boolean isLand = newConfig.orientation == Configuration.ORIENTATION_PORTRAIT;
        if (sp.getBoolean("land", false))
            view.setVisibility(isLand ? View.GONE : View.VISIBLE);
        GetWidthHeight();
        params.x = sp.getInt("x" + (isLand ? "1" : "2"), 0);
        params.y = sp.getInt("y" + (isLand ? "1" : "2"), 0);
        ShapeDrawable oval = new ShapeDrawable(new OvalShape());
        oval.getPaint().setColor(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? getColor(R.color.bg) : 0xffc1e8ff);
        view.setBackground(oval);
        view.setImageResource(R.drawable.fw);
        if (exist) windowManager.updateViewLayout(view, params);

    }

    @Override
    public void onDestroy() {
        unregisterReceiver(myReceiver);
        try {
            windowManager.removeViewImmediate(view);
        } catch (Exception ignored) {
        }
        exist = false;
        listener.disable();
        releasePartialWakeLock();
        releaseWifiLock();
        sp.unregisterOnSharedPreferenceChangeListener(this);
        if (netControl) stopServer();
        super.onDestroy();
    }


    public static int port = 20000;
    private SimpleTcpServer server;

    private void startServer() {
        if (server != null) return;
        server = new SimpleTcpServer(new SimpleTcpServer.TcpConnectionListener() {
            private final HttpRequestParser parser = new HttpRequestParser();

            @Override
            public void onReceive(final byte[] data) {
                parser.add(data);
                final HttpRequest request = parser.parse();
                if (request != null) {
                    output(request);
                    parser.clear();
                }
            }

            @Override
            public void onResponseSent() {
                server.restart();
            }

        }, port);
        server.start();

    }

    void stopServer() {
        server.stop();
        server = null;
    }

    private void output(HttpRequest request) {
        if (server == null) {
            return;
        }
        String target = request.getRequestTarget();
        Log.d("TAG", "output: " + target);
        if (target.equals("/") || target.equals("/index.html")) {
            outputHtml(buildIndexHtml(request), "200 OK");
        } else if (target.equals("/favicon.ico")) {
            byte[] fav = loadBinary("favicon.png");
            if (fav != null) {
                outputPng(fav);
            } else {
                outputHtml("", "200 OK");
            }
        } else {
            try {
                String prefix = target.length() >= 3 ? target.substring(0, 3) : "";
                switch (prefix) {
                    case "/1?":
                        // 局域网亮屏：走 screenoff(false) 统一流程（释放锁、清标志位）
                        // 必须切回主线程，避免后台线程操作 View/监听器
                        if (isServiceOK && mainHandler != null) {
                            mainHandler.post(() -> screenoff(false));
                        }
                        outputHtml("", "200 OK");
                        break;
                    case "/2?":
                        // 局域网息屏：走 screenoff(true) 统一流程（获取唤醒锁/WiFi锁、设置 screenOffByApp、启用方向监听）
                        // 这样息屏后竖起手机仍然可以点亮屏幕，且局域网控制在息屏后仍可响应
                        if (isServiceOK && mainHandler != null) {
                            mainHandler.post(() -> screenoff(true));
                        }
                        outputHtml("", "200 OK");
                        break;
                    default:
                        outputHtml(build404Html(), "404 Not Found");
                        break;
                }
            } catch (Exception ignored) {
            }

        }
    }

    private String buildIndexHtml(HttpRequest request) {
        String nowState = "服务未连接";
        try {
            if (iScreenOff != null) {
                switch (iScreenOff.getNowScreenState()) {
                    case 0:
                        nowState = "息屏";
                        break;
                    case 1:
                        nowState = "亮屏";
                        break;
                    default:
                        nowState = "息屏运行";
                        break;
                }
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        String html = loadHtml("index.html");
        if (html == null) return "";
        return html
                .replace("{{brand}}", Build.BRAND)
                .replace("{{device}}", Build.MODEL + " Android " + Build.VERSION.RELEASE)
                .replace("{{state}}", nowState);
    }

    private String build404Html() {
        return loadHtml("404.html");
    }

    private static final int BUFFER_SIZE = 1024 * 1024;
    private static final byte LF = 0x0a;
    private static final byte CR = 0x0d;

    private void outputHtml(String html, String responseCode) {
        if (html == null) html = "";
        String startLine = "HTTP/1.1 " + responseCode;
        List<String> responseHeaders = new ArrayList<>();
        responseHeaders.add("Content-Type: text/html; charset=UTF-8");
        responseHeaders.add(String.format(Locale.getDefault(), "Content-Length: %d", html.getBytes().length));
        responseHeaders.add("Connection: close");
        StringBuilder builder = new StringBuilder();
        builder.append(startLine).append(new String(new byte[]{CR, LF}));
        for (String responseHeader : responseHeaders) {
            builder.append(responseHeader).append(new String(new byte[]{CR, LF}));
        }
        builder.append(new String(new byte[]{CR, LF}));
        builder.append(html);
        server.output(builder.toString());
    }

    private void outputPng(byte[] png) {
        String startLine = "HTTP/1.1 " + "200 OK";
        List<String> responseHeaders = new ArrayList<>();
        responseHeaders.add("Content-Type: image/png");
        responseHeaders.add(String.format(Locale.getDefault(), "Content-Length: %d", png.length));
        responseHeaders.add("Connection: close");
        StringBuilder builder = new StringBuilder();
        builder.append(startLine).append(new String(new byte[]{CR, LF}));
        for (String responseHeader : responseHeaders) {
            builder.append(responseHeader).append(new String(new byte[]{CR, LF}));
        }
        builder.append(new String(new byte[]{CR, LF}));
        byte[] headerField = builder.toString().getBytes();
        byte[] output = new byte[headerField.length + png.length];
        System.arraycopy(headerField, 0, output, 0, headerField.length);
        System.arraycopy(png, 0, output, headerField.length, png.length);
        server.output(output);
    }

    private String loadHtml(String fileName) {
        byte[] binary = loadBinary(fileName);
        if (binary == null) {
            return null;
        }
        return new String(binary);
    }

    private byte[] loadBinary(String fileName) {
        try {
            InputStream is = getAssets().open(fileName);
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            byte[] chunk = new byte[BUFFER_SIZE];
            BufferedInputStream bis = new BufferedInputStream(is, BUFFER_SIZE);
            try {
                int len;
                while ((len = bis.read(chunk, 0, BUFFER_SIZE)) > 0) {
                    byteStream.write(chunk, 0, len);
                }
                return byteStream.toByteArray();
            } finally {
                try {
                    byteStream.reset();
                    bis.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }


}
