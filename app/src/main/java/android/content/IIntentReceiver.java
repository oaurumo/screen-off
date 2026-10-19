package android.content;

/**
 * 空接口，用于反射调用时匹配 IActivityManager.broadcastIntent 的参数类型。
 * 实际调用时传入 null。从 AIDL 转为 Java 以兼容 AGP 8.x。
 */
public interface IIntentReceiver {
}
