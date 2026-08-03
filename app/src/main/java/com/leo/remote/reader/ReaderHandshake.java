package com.leo.remote.reader;

import android.util.Log;
import com.leo.remote.R;
import java.util.function.IntConsumer;

final class ReaderHandshake {
    private static final String TAG = "UhfReader";
    static final class Result {
        final ReaderModuleInfo moduleInfo;
        final ReaderConfiguration configuration;

        Result(ReaderModuleInfo moduleInfo, ReaderConfiguration configuration) {
            this.moduleInfo = moduleInfo;
            this.configuration = configuration;
        }
    }

    private ReaderHandshake() {}

    static Result perform(UhfSdkGateway gateway) throws ReaderException {
        ReaderModuleInfo info = gateway.readModuleInfo();
        if (info.subtype == ModuleSubtype.UNKNOWN) {
            throw new ReaderException("Unknown RM70XX subtype: " + info.rawSubtype, info.rawSubtype);
        }
        if (isBlank(info.boardSerial) || isBlank(info.boardVersion)
                || isBlank(info.moduleSerial) || isBlank(info.moduleVersion)) {
            throw new ReaderException("RM70XX device information is incomplete", -7);
        }
        int status = gateway.setProtocol(TagProtocol.ISO_18000_6C);
        if (status != 0) {
            throw new ReaderException("Unable to select 6C protocol", status);
        }
        status = gateway.applyInventoryParams(TagProtocol.ISO_18000_6C, 0, 0, 0);
        if (status != 0) {
            throw new ReaderException("Unable to configure inventory", status);
        }
        return new Result(info, gateway.readConfiguration(info.subtype));
    }

    static Result perform(UhfSdkGateway gateway, ReaderConfigCache cache,
            IntConsumer progress) throws ReaderException {
        progress.accept(R.string.handshake_updating_params);
        ReaderModuleInfo info = gateway.readModuleInfo();
        validateModuleInfo(info);

        int status = gateway.setProtocol(TagProtocol.ISO_18000_6C);
        if (status != 0) {
            throw new ReaderException("Unable to select 6C protocol", status);
        }
        ReaderConfiguration fallback = cache.loadConfiguration(info.subtype);
        if (fallback == null) { fallback = ReaderConfigCache.getDefaultConfiguration(info.subtype); }
        status = gateway.applyInventoryParams(TagProtocol.ISO_18000_6C, fallback.inventoryArea,
                fallback.inventoryArea == 0 ? 0 : fallback.inventoryAddress,
                fallback.inventoryArea == 0 ? 0 : fallback.inventoryWordLen);
        if (status != 0) {
            throw new ReaderException("Unable to configure inventory area", status);
        }
        return new Result(info, readConfigurationStepwise(gateway, info.subtype, cache, progress));
    }

    static ReaderConfiguration readConfigurationStepwise(UhfSdkGateway gateway,
            ModuleSubtype subtype, ReaderConfigCache cache, IntConsumer progress) {
        ReaderConfiguration fallback = cache.loadConfiguration(subtype);
        if (fallback == null) { fallback = ReaderConfigCache.getDefaultConfiguration(subtype); }

        progress.accept(R.string.handshake_reading_power);
        int power = fallback.powerTenthsDbm;
        try {
            Integer value = gateway.getPowerTenthsDbm();
            if (value != null) { power = value; }
        } catch (RuntimeException error) {
            Log.w(TAG, "读取功率失败，使用缓存值 " + power, error);
        }

        progress.accept(R.string.handshake_reading_protocol);
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

        progress.accept(R.string.handshake_reading_session);
        int session = fallback.session;
        int target = fallback.target;
        try {
            int[] group = gateway.getQueryGroup(subtype);
            if (group != null && group.length >= 2) {
                session = group[0];
                target = group[1];
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "读取 Session 失败，使用缓存值 S" + session, error);
        }

        progress.accept(R.string.handshake_reading_blf);
        int blf = fallback.blfProfile;
        if (subtype != ModuleSubtype.RM8011) {
            try {
                Integer value = gateway.getBlfProfile();
                if (value != null) { blf = value; }
            } catch (RuntimeException error) {
                Log.w(TAG, "读取 BLF 失败，使用缓存值 " + blf, error);
            }
        }

        progress.accept(R.string.handshake_reading_q);
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
