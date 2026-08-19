package com.leo.uhf.app;

import android.app.Application;
import com.hjq.core.manager.ActivityManager;
import com.leo.uhf.core.aop.Log;
import com.leo.uhf.app.bootstrap.AppInitializer;
import com.leo.uhf.core.manager.OrientationManager;

/** 应用入口 */
public final class App extends Application {
    private static volatile App instance;

    public static App getInstance() { return instance; }

    @Log("启动耗时")
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        OrientationManager.register(this);

        // 如果当前的进程不是主进程的话，则不进行第三方框架的初始化
        if (!ActivityManager.isMainProcess(this)) {
            return;
        }

        AppInitializer.initializeApplication(this);
        // 创建全局读写器会话并初始化一次 JNI。连接页面只复用这个会话。
        AppInitializer.initializeReader(this);
    }

}
