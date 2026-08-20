package com.leo.uhf.rfid.sdk.linkage;

import com.leo.uhf.rfid.api.model.*;

import static org.junit.Assert.assertEquals;

import com.uhf.structures.AntennaPorts;
import com.uhf.structures.LowpowerParams;
import org.junit.Test;

/**
 * 验证原生 SDK 网关的返回码、异常和数据映射。
 */
public class UhfNativeBridgeTest {

    @Test
    public void lowPowerSchedulerSetsAntennaDwellTimeFirst() {
        AntennaPorts antenna = new AntennaPorts();
        LowpowerParams params = new LowpowerParams();

        UhfNativeBridge.prepareLowPowerValues(antenna, params, 0, 30, 100);

        assertEquals(30, antenna.dwellTime);
        assertEquals(0, params.highPerformanceTime);
        assertEquals(30, params.inventoryOnTime);
        assertEquals(100, params.inventoryOffTime);
    }

    @Test
    public void mapsQilianRm8011ToMagicRfLinkageType() {
        assertEquals(1, UhfNativeBridge.LINKAGE_TYPE_QILIAN);
        assertEquals(2, UhfNativeBridge.LINKAGE_TYPE_RM70XX);
        assertEquals(1, UhfNativeBridge.linkageTypeFor(ModuleSubtype.RM8011));
        assertEquals(0, UhfNativeBridge.linkageTypeFor(ModuleSubtype.R2000));
        assertEquals(3, UhfNativeBridge.linkageTypeFor(ModuleSubtype.R2000_PLUS));
    }
}
