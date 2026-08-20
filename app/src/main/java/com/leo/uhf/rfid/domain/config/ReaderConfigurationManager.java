package com.leo.uhf.rfid.domain.config;

import com.leo.uhf.rfid.api.model.*;
import com.leo.uhf.rfid.persistence.ReaderConfigurationStore;
import com.leo.uhf.rfid.sdk.capability.ReaderConfigurationGateway;
import com.leo.uhf.rfid.session.ReaderStatePublisher;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 管理当前读写器参数及按模块型号隔离的持久化缓存。
 */
public final class ReaderConfigurationManager {
    private final ReaderConfigurationGateway gateway;
    private final ReaderConfigurationStore cache;
    private final ReaderStatePublisher publisher;

    private volatile ReaderConfiguration configuration;
    private volatile int inventoryMode = 1;

    public ReaderConfigurationManager(@NonNull ReaderConfigurationGateway gateway,
            @NonNull ReaderConfigurationStore cache, @NonNull ReaderStatePublisher publisher) {
        this.gateway = gateway;
        this.cache = cache;
        this.publisher = publisher;
    }

    @Nullable
    public ReaderConfiguration getConfiguration() {
        return configuration;
    }

    public int getInventoryMode() {
        return inventoryMode;
    }

    public void clear() {
        configuration = null;
    }

    public void restore(@NonNull ReaderConfiguration restored) {
        configuration = restored;
        inventoryMode = restored.inventoryMode;
    }

    public int setInventoryMode(@NonNull ModuleSubtype subtype, int requestedMode) {
        if (requestedMode < 0 || requestedMode > 2) { return inventoryMode; }
        inventoryMode = subtype.supportsInventoryModeSwitch() ? requestedMode : 1;
        if (configuration != null) {
            cache.saveConfiguration(subtype, snapshot());
        }
        publishCurrent();
        return inventoryMode;
    }

    public int setInventoryArea(@NonNull ModuleSubtype subtype, int area, int address, int wordLen) {
        int effectiveAddress = area == 0 ? 0 : address;
        int effectiveLength = area == 0 ? 0 : wordLen;
        int status = gateway.setInventoryArea(area, effectiveAddress, effectiveLength);
        ReaderConfiguration current = configuration;
        if (current == null) { return status; }
        return update(subtype, status, copy(current, current.powerTenthsDbm,
                current.blfProfile, current.session, current.target, current.dynamicQ,
                current.qValue, current.qMinValue, current.qMaxValue, current.qRetryCount,
                current.qThresholdMultiplier, area, effectiveAddress, effectiveLength));
    }

    public int setPower(@NonNull ModuleSubtype subtype, int powerTenthsDbm) {
        ReaderConfiguration current = requireConfiguration();
        return update(subtype, gateway.setPowerTenthsDbm(powerTenthsDbm),
                copy(current, powerTenthsDbm, current.blfProfile, current.session,
                        current.target, current.dynamicQ, current.qValue, current.qMinValue,
                        current.qMaxValue, current.qRetryCount, current.qThresholdMultiplier,
                        current.inventoryArea, current.inventoryAddress,
                        current.inventoryWordLen));
    }

    public int setBlf(@NonNull ModuleSubtype subtype, int profile) {
        ReaderConfiguration current = requireConfiguration();
        return update(subtype, gateway.setBlfProfile(profile),
                copy(current, current.powerTenthsDbm, profile, current.session, current.target,
                        current.dynamicQ, current.qValue, current.qMinValue, current.qMaxValue,
                        current.qRetryCount, current.qThresholdMultiplier, current.inventoryArea,
                        current.inventoryAddress, current.inventoryWordLen));
    }

    public int applySession(@NonNull ModuleSubtype subtype, int session, int selected) {
        ReaderConfiguration current = requireConfiguration();
        return gateway.setSession(subtype, session, current.target, selected);
    }

    public void commitSession(@NonNull ModuleSubtype subtype, int session) {
        ReaderConfiguration current = requireConfiguration();
        update(subtype, 0, copy(current, current.powerTenthsDbm,
                current.blfProfile, session, current.target, current.dynamicQ, current.qValue,
                current.qMinValue, current.qMaxValue, current.qRetryCount,
                current.qThresholdMultiplier, current.inventoryArea, current.inventoryAddress,
                current.inventoryWordLen));
    }

    public void updateProtocolArea(@NonNull ModuleSubtype subtype, int area, int address, int wordLen) {
        ReaderConfiguration current = configuration;
        if (current == null) { return; }
        configuration = copy(current, current.powerTenthsDbm, current.blfProfile,
                current.session, current.target, current.dynamicQ, current.qValue,
                current.qMinValue, current.qMaxValue, current.qRetryCount,
                current.qThresholdMultiplier, area, address, wordLen);
        cache.saveConfiguration(subtype, configuration);
    }

    public void publishCurrent() {
        if (configuration == null) { return; }
        configuration = snapshot();
        publisher.publishConfiguration(configuration);
    }

    private int update(ModuleSubtype subtype, int status, ReaderConfiguration updated) {
        if (status == 0) {
            configuration = updated;
            cache.saveConfiguration(subtype, updated);
            publishCurrent();
        }
        return status;
    }

    private ReaderConfiguration snapshot() {
        ReaderConfiguration current = requireConfiguration();
        return new ReaderConfiguration(current.powerTenthsDbm, inventoryMode,
                current.blfProfile, current.session, current.target, current.dynamicQ,
                current.qValue, current.qMinValue, current.qMaxValue, current.qRetryCount,
                current.qThresholdMultiplier, current.qToggleTarget,
                current.qRepeatUntilNoTags, current.inventoryArea, current.inventoryAddress,
                current.inventoryWordLen);
    }

    private ReaderConfiguration requireConfiguration() {
        ReaderConfiguration current = configuration;
        if (current == null) {
            throw new IllegalStateException("Reader configuration is unavailable");
        }
        return current;
    }

    private ReaderConfiguration copy(ReaderConfiguration current, int powerTenthsDbm,
            int blfProfile, int session, int target, boolean dynamicQ, int qValue,
            int qMinValue, int qMaxValue, int qRetryCount, int qThresholdMultiplier,
            int inventoryArea, int inventoryAddress, int inventoryWordLen) {
        return new ReaderConfiguration(powerTenthsDbm, inventoryMode, blfProfile, session,
                target, dynamicQ, qValue, qMinValue, qMaxValue, qRetryCount,
                qThresholdMultiplier, current.qToggleTarget, current.qRepeatUntilNoTags,
                inventoryArea, inventoryAddress, inventoryWordLen);
    }
}
