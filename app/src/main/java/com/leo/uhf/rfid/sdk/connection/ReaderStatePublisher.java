package com.leo.uhf.rfid.sdk.connection;

import com.leo.uhf.rfid.sdk.model.*;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

/**
 * Reader 状态发布器
 * 
 * 负责管理观察者并发布状态变更通知。
 * 使用线程安全的 CopyOnWriteArraySet 存储观察者，
 * 通过 Handler 确保所有回调在主线程执行。
 * 
 * 职责：
 * - 观察者注册与移除
 * - 状态变更通知
 * - 盘点数据更新通知
 * - 配置变更通知
 * - 主线程切换
 */
public final class ReaderStatePublisher {
    
    private final Consumer<Runnable> dispatcher;
    private final CopyOnWriteArraySet<ReaderObserver> observers = new CopyOnWriteArraySet<>();
    
    public ReaderStatePublisher() {
        this(new Handler(Looper.getMainLooper())::post);
    }

    public ReaderStatePublisher(@NonNull Consumer<Runnable> dispatcher) {
        this.dispatcher = dispatcher;
    }
    
    /**
     * 添加观察者
     * 
     * @param observer 观察者实例
     */
    public void addObserver(@NonNull ReaderObserver observer) {
        observers.add(observer);
    }

    void addObserver(@NonNull ReaderObserver observer, @NonNull Runnable initialStateDispatch) {
        observers.add(observer);
        dispatch(initialStateDispatch);
    }
    
    /**
     * 移除观察者
     * 
     * @param observer 观察者实例
     */
    public void removeObserver(@NonNull ReaderObserver observer) {
        observers.remove(observer);
    }
    
    /**
     * 发布 Reader 状态变更
     * 
     * @param state 新的 Reader 状态
     */
    public void publishState(@NonNull ReaderState state) {
        dispatch(() -> {
            for (ReaderObserver observer : observers) {
                observer.onReaderStateChanged(state);
            }
        });
    }
    
    /**
     * 发布盘点数据更新
     *
     * @param items 盘点项列表
     * @param totalReads 总读取次数
     */
    public void publishInventoryUpdate(@NonNull List<InventoryItem> items, long totalReads) {
        dispatch(() -> {
            for (ReaderObserver observer : observers) {
                observer.onInventoryChanged(items, totalReads);
            }
        });
    }
    
    /**
     * 发布当前标签变更
     * 
     * @param tag 当前标签，null 表示清除
     */
    public void publishCurrentTag(@Nullable ReaderTag tag) {
        dispatch(() -> {
            for (ReaderObserver observer : observers) {
                observer.onCurrentTagChanged(tag);
            }
        });
    }
    
    /**
     * 发布配置变更
     * 
     * @param configuration 新的配置，null 表示未配置
     */
    public void publishConfiguration(@Nullable ReaderConfiguration configuration) {
        dispatch(() -> {
            for (ReaderObserver observer : observers) {
                observer.onReaderConfigurationChanged(configuration);
            }
        });
    }
    
    /**
     * 发布 Mask 配置变更
     * 
     * @param mask Mask 配置，null 表示清除
     */
    public void publishMask(@Nullable InventoryMaskConfig mask) {
        dispatch(() -> {
            for (ReaderObserver observer : observers) {
                observer.onInventoryMaskChanged(mask);
            }
        });
    }
    
    /**
     * 通知意外断开事件
     * 
     * @param reason 断开原因
     */
    public void notifyUnexpectedDisconnect(@NonNull DisconnectReason reason) {
        dispatch(() -> {
            for (ReaderObserver observer : observers) {
                observer.onReaderUnexpectedDisconnect(reason);
            }
        });
    }

    /**
     * 发布单标签 Mask 配置变更。
     *
     * @param mask Mask 配置，null 表示清除
     */
    public void publishSingleTagMask(@Nullable InventoryMaskConfig mask) {
        dispatch(() -> {
            for (ReaderObserver observer : observers) {
                observer.onSingleTagMaskChanged(mask);
            }
        });
    }
    
    /**
     * 获取当前观察者数量
     * 
     * @return 观察者数量
     */
    public int getObserverCount() {
        return observers.size();
    }
    
    /**
     * 清除所有观察者（用于测试或清理）
     */
    public void clearObservers() {
        observers.clear();
    }

    private void dispatch(@NonNull Runnable callback) {
        dispatcher.accept(callback);
    }
}
