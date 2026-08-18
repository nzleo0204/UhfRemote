package com.leo.rfid.sdk.connect;

import com.leo.rfid.sdk.model.*;
import com.leo.rfid.sdk.storage.ReaderConfigCache;
import com.leo.rfid.sdk.storage.ReaderConfigurationStore;
import com.leo.rfid.sdk.bridge.*;

import android.util.Log;
import java.util.function.Consumer;

/**
 * 执行读写器握手，校验模块信息并读取设备当前参数。
 */
final class ReaderHandshake {
    private static final String TAG = "UhfReader";
    private static final long MODULE_INFO_SETTLE_MS = 200L;
    /** 握手成功后返回的模块信息与参数快照。 */
    static final class Result {
        final ReaderModuleInfo moduleInfo;
        final ReaderConfiguration configuration;

        Result(ReaderModuleInfo moduleInfo, ReaderConfiguration configuration) {
            this.moduleInfo = moduleInfo;
            this.configuration = configuration;
        }
    }

    private ReaderHandshake() {}

    /**
     * 执行基础握手并读取设备参数。
     */
    static Result perform(ReaderTransportGateway transport,
            ReaderConfigurationGateway configuration, InventoryBridge inventory)
            throws ReaderException {
        ReaderModuleInfo info = readModuleInfoAfterStoppingInventory(transport, inventory);
        if (info.subtype == ModuleSubtype.UNKNOWN) {
            throw new ReaderException("Unknown RM70XX subtype: " + info.rawSubtype, info.rawSubtype);
        }
        if (isBlank(info.boardSerial) || isBlank(info.boardVersion)
                || isBlank(info.moduleSerial) || isBlank(info.moduleVersion)) {
            throw new ReaderException("RM70XX device information is incomplete", -7);
        }
        int status = configuration.setProtocol(TagProtocol.ISO_18000_6C);
        if (status != 0) {
            throw new ReaderException("Unable to select 6C protocol", status);
        }
        status = inventory.applyInventoryParams(TagProtocol.ISO_18000_6C, 0, 0, 0);
        if (status != 0) {
            throw new ReaderException("Unable to configure inventory", status);
        }
        return new Result(info, configuration.readConfiguration(info.subtype));
    }

    /**
     * 执行带分步进度通知和缓存回退的完整握手。
     */
    static Result perform(ReaderTransportGateway transport,
            ReaderConfigurationGateway configuration, InventoryBridge inventory,
            ReaderConfigurationStore cache, Consumer<ReaderProgress> progress)
            throws ReaderException {
        ReaderModuleInfo info = readModuleInfoAfterStoppingInventory(transport, inventory);
        validateModuleInfo(info);

        progress.accept(ReaderProgress.UPDATING_PARAMETERS);
        int status = configuration.setProtocol(TagProtocol.ISO_18000_6C);
        if (status != 0) {
            throw new ReaderException("Unable to select 6C protocol", status);
        }
        ReaderConfiguration fallback = cache.loadConfiguration(info.subtype);
        if (fallback == null) { fallback = ReaderConfigCache.getDefaultConfiguration(info.subtype); }
        status = inventory.applyInventoryParams(TagProtocol.ISO_18000_6C, fallback.inventoryArea,
                fallback.inventoryArea == 0 ? 0 : fallback.inventoryAddress,
                fallback.inventoryArea == 0 ? 0 : fallback.inventoryWordLen);
        if (status != 0) {
            throw new ReaderException("Unable to configure inventory area", status);
        }
        return new Result(info, readConfigurationStepwise(
                configuration, info.subtype, cache, progress));
    }

    private static ReaderModuleInfo readModuleInfoAfterStoppingInventory(
            ReaderTransportGateway transport, InventoryBridge inventory)
            throws ReaderException {
        inventory.stopInventory();
        try {
            Thread.sleep(MODULE_INFO_SETTLE_MS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ReaderException("Interrupted while preparing RM70XX module", -1);
        }
        return transport.readModuleInfo();
    }

    /**
     * 分步骤读取设备参数；单项读取失败时使用对应模块的缓存值。
     */
    static ReaderConfiguration readConfigurationStepwise(ReaderConfigurationGateway gateway,
            ModuleSubtype subtype, ReaderConfigurationStore cache,
            Consumer<ReaderProgress> progress) {
        ReaderConfiguration fallback = cache.loadConfiguration(subtype);
        if (fallback == null) { fallback = ReaderConfigCache.getDefaultConfiguration(subtype); }

        progress.accept(ReaderProgress.READING_POWER);
        int power = fallback.powerTenthsDbm;
        try {
            Integer value = gateway.getPowerTenthsDbm();
            if (value != null) { power = value; }
        } catch (RuntimeException error) {
            Log.w(TAG, "读取功率失败，使用缓存值 " + power, error);
        }

        progress.accept(ReaderProgress.READING_PROTOCOL);
        int inventoryArea = fallback.inventoryArea;
        int inventoryAddress = fallback.inventoryAddress;
        int inventoryWordLen = fallback.inventoryWordLen;
        try {
            int[] area = gateway.getInventoryArea();
            if (area != null && area.length >= 3) {
                inventoryArea = area[0];
                inventoryAddress = area[1];
                inventoryWordLen = area[2];
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "读取盘点区域失败，使用缓存值", error);
        }

        progress.accept(ReaderProgress.READING_SESSION);
        int session = fallback.session;
        int target = fallback.target;
        int selected = cache.loadSelected(subtype);
        try {
            int[] values = gateway.getQueryValues(subtype);
            if (values != null && values.length >= 3) {
                session = values[0];
                target = values[1];
                selected = values[2];
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "读取 Session 失败，使用缓存值 S" + session, error);
        }
        cache.saveSelected(subtype, selected);

        progress.accept(ReaderProgress.READING_BLF);
        int blf = fallback.blfProfile;
        if (subtype != ModuleSubtype.RM8011) {
            try {
                Integer value = gateway.getBlfProfile();
                if (value != null) { blf = value; }
            } catch (RuntimeException error) {
                Log.w(TAG, "读取 BLF 失败，使用缓存值 " + blf, error);
            }
        }

        boolean dynamic = fallback.dynamicQ;
        int q = fallback.qValue;
        int minQ = fallback.qMinValue;
        int maxQ = fallback.qMaxValue;
        int retry = fallback.qRetryCount;
        int threshold = fallback.qThresholdMultiplier;
        int toggle = fallback.qToggleTarget;
        int repeat = fallback.qRepeatUntilNoTags;
        try {
            ReaderQParams value = gateway.getQParams(subtype);
            if (value != null) {
                dynamic = value.dynamic;
                q = value.qValue;
                minQ = value.minQ;
                maxQ = value.maxQ;
                retry = value.retryCount;
                threshold = value.thresholdMultiplier;
                toggle = value.toggleTarget;
                repeat = value.repeatUntilNoTags;
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "读取 Q 值失败，使用缓存值 Q" + q, error);
        }

        ReaderConfiguration result = new ReaderConfiguration(power, fallback.inventoryMode, blf,
                session, target, dynamic, q, minQ, maxQ, retry, threshold, toggle, repeat,
                inventoryArea, inventoryAddress, inventoryWordLen);
        cache.saveConfiguration(subtype, result);
        return result;
    }

    private static void validateModuleInfo(ReaderModuleInfo info) throws ReaderException {
        if (info == null || info.subtype == ModuleSubtype.UNKNOWN) {
            int raw = info == null ? Integer.MIN_VALUE : info.rawSubtype;
            throw new ReaderException("Unknown RM70XX subtype: " + raw, raw);
        }
        if (isBlank(info.boardSerial) || isBlank(info.boardVersion)
                || isBlank(info.moduleSerial) || isBlank(info.moduleVersion)) {
            throw new ReaderException("RM70XX device information is incomplete", -7);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
