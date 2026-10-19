package com.tile.screenoff;

import android.app.IApplicationThread;
import android.content.IIntentReceiver;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Scanner;

public class ScreenController {


    //发送广播需要的三个东西
    static boolean isBroadcastSent = false;
    static Intent intent = null;
    static Object iActivityManagerObj = null;

    // ===== 诊断日志：同时输出到 logcat 和文件 =====
    private static final String TAG = "ScreenController";

    static void diag(String msg) {
        Log.e(TAG, msg);
        String line = new java.text.SimpleDateFormat("MM-dd HH:mm:ss.SSS", java.util.Locale.US)
                .format(new java.util.Date()) + " " + msg + "\n";
        String[] paths = {
                "/sdcard/Android/data/com.tile.screenoff/files/controller.log",
                "/data/local/tmp/screenoff_controller.log"
        };
        for (String p : paths) {
            try {
                java.io.File f = new java.io.File(p);
                if (f.getParentFile() != null) f.getParentFile().mkdirs();
                java.io.FileOutputStream fos = new java.io.FileOutputStream(f, true);
                fos.write(line.getBytes());
                fos.close();
                return;
            } catch (Exception ignored) {
            }
        }
    }


    public enum ScreenState {
        STATE_OFF, STATE_ON, STATE_SPECIAL
    }

    static ScreenState screenState = ScreenState.STATE_ON;


    private static final Class<?> CLASS;
    public static final int POWER_MODE_OFF = 0;
    public static final int POWER_MODE_NORMAL = 2;

    // Android 15+ (API 35) 新增的 DisplayManagerGlobal.requestDisplayPower 方法
    // 注意：Android 15 签名为 requestDisplayPower(int, boolean)
    //      Android 16 签名为 requestDisplayPower(int, int)，boolean 版已删除
    private static final boolean USE_REQUEST_DISPLAY_POWER = Build.VERSION.SDK_INT >= 35;
    private static Object displayManagerGlobalInstance;
    private static Method requestDisplayPowerMethod;
    // Android 16+ 的 (int displayId, int state) 签名：state 0=STATE_UNKNOWN(亮) 1=STATE_OFF(灭)
    private static boolean requestDisplayPowerUseIntState;

    static {
        try {
            CLASS = Class.forName("android.view.SurfaceControl");
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
        // 初始化 DisplayManagerGlobal (用于 Android 15+ 的 requestDisplayPower)
        if (USE_REQUEST_DISPLAY_POWER) {
            try {
                Class<?> dmgClass = Class.forName("android.hardware.display.DisplayManagerGlobal");
                Method getInstanceMethod = dmgClass.getDeclaredMethod("getInstance");
                displayManagerGlobalInstance = getInstanceMethod.invoke(null);
                if (displayManagerGlobalInstance == null) {
                    Log.e(TAG, "DisplayManagerGlobal.getInstance() returned null");
                }
            } catch (Throwable e) {
                Log.e(TAG, "Could not get DisplayManagerGlobal instance", e);
                displayManagerGlobalInstance = null;
            }
            if (displayManagerGlobalInstance != null) {
                try {
                    Class<?> dmgClass = displayManagerGlobalInstance.getClass();
                    if (Build.VERSION.SDK_INT >= 36) {
                        // Android 16: requestDisplayPower(int displayId, int state)
                        requestDisplayPowerMethod = dmgClass.getMethod("requestDisplayPower", int.class, int.class);
                        requestDisplayPowerUseIntState = true;
                    } else {
                        // Android 15: requestDisplayPower(int displayId, boolean on)
                        requestDisplayPowerMethod = dmgClass.getMethod("requestDisplayPower", int.class, boolean.class);
                        requestDisplayPowerUseIntState = false;
                    }
                } catch (Throwable e) {
                    Log.e(TAG, "Could not find requestDisplayPower method", e);
                    requestDisplayPowerMethod = null;
                }
            }
        }
    }
    public static boolean useDisplayControl =
            Build.VERSION.SDK_INT >= 34;
    private static Method getBuiltInDisplayMethod;
    private static Method setDisplayPowerModeMethod;
    private static Method getPhysicalDisplayIdsMethod;
    private static Method getPhysicalDisplayTokenMethod;
    private static boolean listenVolumeKey = false;
    static Thread h1;
    static Process listenVolumeKeyProcess;

    private static Method getGetBuiltInDisplayMethod() throws NoSuchMethodException {
        if (getBuiltInDisplayMethod == null)
            getBuiltInDisplayMethod = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ? CLASS.getMethod("getBuiltInDisplay", int.class) : CLASS.getMethod("getInternalDisplayToken");
        return getBuiltInDisplayMethod;
    }

    public static IBinder getBuiltInDisplay() {
        try {
            // Change the power mode for all physical displays
            Log.e("getBuiltInDisplay","Android 14"+useDisplayControl);
            if (useDisplayControl) {
                long[] physicalDisplayIds = DisplayControl.getPhysicalDisplayIds();
                if (physicalDisplayIds == null) {
                    Log.e("getBuiltInDisplay", "Could not get physical display ids");
                    return null;
                }

                for (long physicalDisplayId : physicalDisplayIds) {
                    return DisplayControl.getPhysicalDisplayToken(
                            physicalDisplayId);

                }
            } else {
                Method method = getGetBuiltInDisplayMethod();
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    // call getBuiltInDisplay(0)
                    return (IBinder) method.invoke(null, 0);
                }

                // call getInternalDisplayToken()
                return (IBinder) method.invoke(null);
            }
        } catch(InvocationTargetException | IllegalAccessException | NoSuchMethodException e){
            Log.e("Could not invoke method", String.valueOf(e));
            return null;
        }
        return null;
    }

    /**
     * 获取所有物理显示器的 token 列表（参考 scrcpy 实现，遍历所有显示器而非仅第一个）
     * 优先使用 SurfaceControl.getPhysicalDisplayIds（如果可用），否则使用 DisplayControl
     */
    public static IBinder[] getAllPhysicalDisplayTokens() {
        try {
            // 优先尝试 SurfaceControl.getPhysicalDisplayIds（部分 Android 版本仍保留此方法）
            if (hasSurfaceControlGetPhysicalDisplayIds()) {
                long[] physicalDisplayIds = getSurfaceControlPhysicalDisplayIds();
                if (physicalDisplayIds != null && physicalDisplayIds.length > 0) {
                    IBinder[] tokens = new IBinder[physicalDisplayIds.length];
                    for (int i = 0; i < physicalDisplayIds.length; i++) {
                        tokens[i] = getSurfaceControlPhysicalDisplayToken(physicalDisplayIds[i]);
                    }
                    return tokens;
                }
            }

            // 回退到 DisplayControl（Android 14+）
            if (useDisplayControl) {
                long[] physicalDisplayIds = DisplayControl.getPhysicalDisplayIds();
                if (physicalDisplayIds == null || physicalDisplayIds.length == 0) {
                    Log.e("getAllPhysicalDisplayTokens", "Could not get physical display ids");
                    return new IBinder[0];
                }
                IBinder[] tokens = new IBinder[physicalDisplayIds.length];
                for (int i = 0; i < physicalDisplayIds.length; i++) {
                    tokens[i] = DisplayControl.getPhysicalDisplayToken(physicalDisplayIds[i]);
                }
                return tokens;
            } else {
                IBinder token = getBuiltInDisplay();
                if (token != null) {
                    return new IBinder[]{token};
                }
                return new IBinder[0];
            }
        } catch (Exception e) {
            Log.e("getAllPhysicalDisplayTokens", "Error", e);
            return new IBinder[0];
        }
    }

    /**
     * 检查 SurfaceControl 是否有 getPhysicalDisplayIds 方法
     */
    private static boolean hasSurfaceControlGetPhysicalDisplayIds() {
        try {
            getGetPhysicalDisplayIdsMethod();
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private static Method getGetPhysicalDisplayIdsMethod() throws NoSuchMethodException {
        if (getPhysicalDisplayIdsMethod == null) {
            getPhysicalDisplayIdsMethod = CLASS.getMethod("getPhysicalDisplayIds");
        }
        return getPhysicalDisplayIdsMethod;
    }

    @SuppressWarnings("unchecked")
    private static long[] getSurfaceControlPhysicalDisplayIds() {
        try {
            Method method = getGetPhysicalDisplayIdsMethod();
            return (long[]) method.invoke(null);
        } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
            Log.e("ScreenController", "getPhysicalDisplayIds failed", e);
            return null;
        }
    }

    private static Method getGetPhysicalDisplayTokenMethod() throws NoSuchMethodException {
        if (getPhysicalDisplayTokenMethod == null) {
            getPhysicalDisplayTokenMethod = CLASS.getMethod("getPhysicalDisplayToken", long.class);
        }
        return getPhysicalDisplayTokenMethod;
    }

    private static IBinder getSurfaceControlPhysicalDisplayToken(long physicalDisplayId) {
        try {
            Method method = getGetPhysicalDisplayTokenMethod();
            return (IBinder) method.invoke(null, physicalDisplayId);
        } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
            Log.e("ScreenController", "getPhysicalDisplayToken failed", e);
            return null;
        }
    }

    private static Method getSetDisplayPowerModeMethod() throws NoSuchMethodException {
        if (setDisplayPowerModeMethod == null)
            setDisplayPowerModeMethod = CLASS.getMethod("setDisplayPowerMode", IBinder.class, int.class);
        return setDisplayPowerModeMethod;
    }

    public static void setDisplayPowerMode(IBinder displayToken, int mode) {
        try {
            Method method = getSetDisplayPowerModeMethod();
            method.invoke(null, displayToken, mode);
        } catch (InvocationTargetException | IllegalAccessException |
                 NoSuchMethodException ignored) {
        }
    }

    /**
     * 使用 SurfaceControl.setDisplayPowerMode 为所有物理显示器设置电源模式
     */
    public static void setDisplayPowerModeForAll(int mode) {
        IBinder[] tokens = getAllPhysicalDisplayTokens();
        for (IBinder token : tokens) {
            if (token != null) {
                setDisplayPowerMode(token, mode);
            }
        }
    }

    /**
     * Android 15+ 使用 DisplayManagerGlobal.requestDisplayPower 控制显示器电源
     * 这是 Android 15+ 推荐的方式，因为 setDisplayPowerMode 在部分设备上不可靠
     */
    public static boolean requestDisplayPower(boolean on) {
        if (displayManagerGlobalInstance == null || requestDisplayPowerMethod == null) {
            diag("requestDisplayPower: DisplayManagerGlobal not available");
            return false;
        }
        try {
            // displayId 0 为主显示器
            if (requestDisplayPowerUseIntState) {
                // Android 16: state 0=STATE_UNKNOWN(恢复应有状态=亮), 1=STATE_OFF(灭)
                int state = on ? 0 : 1;
                return (boolean) requestDisplayPowerMethod.invoke(displayManagerGlobalInstance, 0, state);
            } else {
                // Android 15: boolean on
                return (boolean) requestDisplayPowerMethod.invoke(displayManagerGlobalInstance, 0, on);
            }
        } catch (Throwable e) {
            diag("requestDisplayPower failed: " + e);
            return false;
        }
    }

    /**
     * 通过 shell 命令执行并记录结果
     */
    public static void runShell(String cmd) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            int code = process.waitFor();
            diag("shell '" + cmd + "' exit=" + code);
        } catch (Exception e) {
            diag("shell '" + cmd + "' failed: " + e);
        }
    }

    /**
     * 通过 shell 命令控制显示器电源（Android 15+ 支持 cmd display power-on/off）
     * 作为最后的回退方案
     */
    public static void setDisplayPowerByShell(boolean on) {
        String cmd = on ? "cmd display power-on 0" : "cmd display power-off 0";
        runShell(cmd);
    }

    /**
     * 统一的显示器电源控制方法（点亮时尝试所有手段，确保成功）：
     * 1. SurfaceControl.setDisplayPowerMode（遍历所有显示器）
     * 2. Android 15+ 使用 DisplayManagerGlobal.requestDisplayPower
     * 3. 点亮时追加 shell 回退（cmd display power-on + KEYCODE_WAKEUP）
     */
    public static void setDisplayPower(boolean on) {
        int mode = on ? POWER_MODE_NORMAL : POWER_MODE_OFF;
        diag("setDisplayPower(" + on + ") begin, SDK=" + Build.VERSION.SDK_INT);

        // 1. SurfaceControl.setDisplayPowerMode 遍历所有显示器
        try {
            IBinder[] tokens = getAllPhysicalDisplayTokens();
            diag("physical display tokens count=" + tokens.length);
            for (IBinder token : tokens) {
                if (token != null) {
                    setDisplayPowerMode(token, mode);
                }
            }
        } catch (Throwable t) {
            diag("setDisplayPowerModeForAll error: " + t);
        }

        // 2. Android 15+ 使用 requestDisplayPower
        if (USE_REQUEST_DISPLAY_POWER) {
            boolean ok = requestDisplayPower(on);
            diag("requestDisplayPower(" + on + ") -> " + ok);
        }

        // 3. 点亮屏幕时追加 shell 回退，确保任何路径下都能点亮
        if (on) {
            setDisplayPowerByShell(true);
            runShell("input keyevent KEYCODE_WAKEUP");
        }
        diag("setDisplayPower(" + on + ") end");
    }

    public static void main(String[] args) {
        diag("ScreenController main() started, SDK=" + Build.VERSION.SDK_INT + ", useDisplayControl=" + useDisplayControl + ", USE_REQUEST_DISPLAY_POWER=" + USE_REQUEST_DISPLAY_POWER
                + ", dmgInstance=" + (displayManagerGlobalInstance != null) + ", dmgMethod=" + (requestDisplayPowerMethod != null) + ", intState=" + requestDisplayPowerUseIntState);
        //检查权限
        int uid = android.os.Process.myUid();
        if (uid != 0 && uid != 2000) {
            System.err.printf("Insufficient permission! Need to be launched by adb (uid 2000) or root (uid 0), but your uid is %d \n", uid);
            System.exit(-1);
            return;
        }

        System.out.println("Start ScreenController Service. Enter \"exit\" here at any time to exit.");

        isBroadcastSent = sendBinderToAppByStickyBroadcast();//发送广播，将binder传给APP
        if (!isBroadcastSent) {
            System.err.println("Failed to send broadcast!");
            System.exit(-1);
            return;
        }

        //加入JVM异常关闭时的处理程序
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                if (isBroadcastSent) isBroadcastSent = !removeStickyBroadcast();
            }
        });

        try {
            Scanner scanner = new Scanner(System.in);
            //用来保持进程不退出，同时如果用户输入exit则程序退出
            String inline;
            while ((inline = scanner.nextLine()) != null) {
                if (inline.equals("exit"))
                    break;
            }
            scanner.close();
        } catch (Exception unused) {
            //用户使用nohup命令启动，scanner捕捉不到任何输入,会抛出异常。
            //阻塞主线程保持进程存活（不能用 while(true); 忙等，否则会占满一个 CPU 核心导致耗电）
            try {
                new java.util.concurrent.CountDownLatch(1).await();
            } catch (InterruptedException ignored) {
            }
        }


        if (isBroadcastSent) isBroadcastSent = !removeStickyBroadcast();
        System.out.println("Stop ScreenController Service.\n");
    }

    private static void stopListenVolumeKey() {
        listenVolumeKey = false;
        if (Build.VERSION.SDK_INT >= 26) {
            listenVolumeKeyProcess.destroyForcibly();
        } else {
            listenVolumeKeyProcess.destroy();
        }
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                h1.interrupt();
            }
        }, 2000);
    }

    private static void startListenVolumeKey() {
        if (listenVolumeKey) return;
        listenVolumeKey = true;
        h1 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    listenVolumeKeyProcess = Runtime.getRuntime().exec("getevent");
                    BufferedReader mReader = new BufferedReader(new InputStreamReader(listenVolumeKeyProcess.getInputStream()));
                    String inline;
                    while ((inline = mReader.readLine()) != null) {
                        if (!listenVolumeKey) break;
                        if (inline.endsWith("0000 0000 00000000")) continue;
                        if (inline.endsWith("0001 0072 00000001") || inline.endsWith("0001 0073 00000001")) {

                            setDisplayPower(true);
                        }
                    }
                    mReader.close();
                    listenVolumeKeyProcess.waitFor();
                    if (Build.VERSION.SDK_INT >= 26) {
                        listenVolumeKeyProcess.destroyForcibly();
                    } else {
                        listenVolumeKeyProcess.destroy();
                    }
                } catch (Exception ignored) {
                }
            }
        });
        h1.start();
    }

    private static boolean sendBinderToAppByStickyBroadcast() {

        try {
            //生成binder
            IBinder binder = new IScreenOff.Stub() {
                @Override
                public void setPowerMode(boolean turnOff) throws RemoteException {
                    diag("setPowerMode(turnOff=" + turnOff + ") called, current state=" + screenState);
                    if (turnOff) {
                        // 允许从 STATE_ON 或 STATE_OFF 状态关闭屏幕
                        if (screenState == ScreenState.STATE_ON || screenState == ScreenState.STATE_OFF) {
                            setDisplayPower(false);
                            screenState = ScreenState.STATE_SPECIAL;
                            diag("screen turned off, state -> STATE_SPECIAL");
                        } else {
                            diag("turn-off ignored, already STATE_SPECIAL");
                        }
                    } else {
                        // 允许从 STATE_SPECIAL 或 STATE_OFF 状态点亮屏幕
                        // 修复 Android 16 上 ACTION_SCREEN_OFF 导致状态变为 STATE_OFF 后无法点亮的问题
                        if (screenState == ScreenState.STATE_SPECIAL || screenState == ScreenState.STATE_OFF) {
                            setDisplayPower(true);
                            screenState = ScreenState.STATE_ON;
                            diag("screen turned on, state -> STATE_ON");
                        } else {
                            diag("turn-on ignored, state already STATE_ON");
                        }
                    }
                }

                @Override
                public int getNowScreenState() throws RemoteException {
                    return screenState.ordinal();
                }

                @Override
                public void updateNowScreenState(boolean isScreenOn) throws RemoteException {
                    screenState = isScreenOn ? ScreenState.STATE_ON : ScreenState.STATE_OFF;
                }

                @Override
                public void closeAndExit() throws RemoteException {
                    if (isBroadcastSent) isBroadcastSent = !removeStickyBroadcast();
                    System.out.println("Stop ScreenController Service.\n");
                    System.exit(0);
                }

            };

            //把binder填到一个可以用Intent来传递的容器中
            BinderContainer binderContainer = new BinderContainer(binder);
            // 创建 Intent 对象，并将binder作为附加参数
            intent = new Intent("intent.screenoff.sendBinder");
            // 必须显式指定包名：Android 16 对 shell(uid 2000) 空身份发出的隐式广播
            // 要求接收方持有 COARSE_LOCATION appop，否则直接跳过投递
            intent.setPackage("com.tile.screenoff");
            intent.putExtra("binder", binderContainer);

            // 获取 IActivityManager 类
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                iActivityManagerObj = Class.forName("android.app.IActivityManager$Stub").getMethod("asInterface", IBinder.class).invoke(null, Class.forName("android.os.ServiceManager").getDeclaredMethod("getService", String.class).invoke(null, "activity"));
            } else {
                Class<?> activityManagerNativeClass = Class.forName("android.app.ActivityManagerNative");
                Method getDefaultMethod = activityManagerNativeClass.getMethod("getDefault");
                iActivityManagerObj = getDefaultMethod.invoke(activityManagerNativeClass);
            }

            // 获取 broadcastIntent 方法
            Method broadcastIntentMethod = Class.forName("android.app.IActivityManager").getDeclaredMethod(
                    "broadcastIntent",
                    IApplicationThread.class,
                    Intent.class,
                    String.class,
                    IIntentReceiver.class,
                    int.class,
                    String.class,
                    Bundle.class,
                    String[].class,
                    int.class,
                    Bundle.class,
                    boolean.class,
                    boolean.class,
                    int.class
            );
            // 调用 broadcastIntent 方法，发送粘性广播
            broadcastIntentMethod.invoke(
                    iActivityManagerObj,
                    null,
                    intent,
                    null,
                    null,
                    -1,
                    null,
                    null,
                    null,
                    0,
                    null,
                    false,
                    true,
                    -1
            );

        } catch (Exception e) {

            return false;
        }
        return true;
    }

    private static boolean removeStickyBroadcast() {
        if (intent == null || iActivityManagerObj == null) return true;

        try {
            // 获取 unbroadcastIntent 方法
            Method unbroadcastIntentMethod = Class.forName("android.app.IActivityManager").getDeclaredMethod("unbroadcastIntent", IApplicationThread.class, Intent.class, int.class);
            // 调用 broadcastIntent 方法，发送粘性广播
            unbroadcastIntentMethod.invoke(iActivityManagerObj, null, intent, -1);
            intent = null;
            iActivityManagerObj = null;
        } catch (Exception e) {
            System.err.println("Failed to remove broadcast!");
            return false;
        }
        return true;
    }
}
