# 忽略警告
#-ignorewarning

# 混淆保护自己项目的部分代码以及引用的第三方jar包
#-libraryjars libs/xxxxxxxxx.jar

# ==================== UHF RFID SDK 混淆规则 ====================

# 保留 UHF SDK 所有类和方法（第三方 native 库）
-keep class com.uhf.** { *; }
-keep interface com.uhf.** { *; }
-keep enum com.uhf.** { *; }

# 保留 Reader 核心类（避免反射调用问题）
-keep class com.leo.remote.rfid.** { *; }

# 保留观察者接口的所有方法
-keep interface com.leo.remote.rfid.sdk.connection.ReaderObserver {
    public <methods>;
}

# 保留实现 ReaderObserver 的类的回调方法
-keepclassmembers class * implements com.leo.remote.rfid.sdk.connection.ReaderObserver {
    public <methods>;
}

# 保留数据模型类（可能用于序列化）
-keep class com.leo.remote.rfid.sdk.model.ReaderState { *; }
-keep class com.leo.remote.rfid.sdk.model.ReaderConfiguration { *; }
-keep class com.leo.remote.rfid.sdk.model.ReaderTag { *; }
-keep class com.leo.remote.rfid.sdk.model.InventoryItem { *; }
-keep class com.leo.remote.rfid.sdk.model.ReaderModuleInfo { *; }

# 保留枚举类
-keepclassmembers enum com.leo.remote.rfid.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ==================== AOP 相关 ====================

# 不混淆被 Log 注解的方法信息
-keepclassmembernames class ** {
    @com.leo.remote.aop.Log <methods>;
}

# 保留被 AOP 注解标记的方法
-keepclassmembers class * {
    @com.flyjingfish.android_aop_annotation.* <methods>;
}

# ==================== MMKV 存储 ====================

# MMKV 已经在库中配置，但确保关键类不被混淆
-keep class com.tencent.mmkv.** { *; }

# ==================== Gson 序列化 ====================

# 保留 Gson 相关（如果有网络请求）
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ==================== 其他配置 ====================

# 保留行号信息（便于调试崩溃日志）
-keepattributes SourceFile,LineNumberTable

# 保留注解
-keepattributes *Annotation*

# 保留泛型信息
-keepattributes Signature
