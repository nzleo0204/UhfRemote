package com.leo.remote.rfid.sdk.nativebridge;

import com.leo.remote.rfid.sdk.model.*;

import static org.junit.Assert.assertEquals;

import com.uhf.structures.AntennaPorts;
import com.uhf.structures.LowpowerParams;
import org.junit.Test;

/**
 * 验证原生 SDK 网关的返回码、异常和数据映射。
 */
public class NativeUhfSdkGatewayTest {

    @Test
    public void lowPowerSchedulerSetsAntennaDwellTimeFirst() {
        AntennaPorts antenna = new AntennaPorts();
        LowpowerParams params = new LowpowerParams();

        NativeUhfSdkGateway.prepareLowPowerValues(antenna, params, 0, 30, 100);

        assertEquals(30, antenna.dwellTime);
        assertEquals(0, params.highPerformanceTime);
        assertEquals(30, params.inventoryOnTime);
        assertEquals(100, params.inventoryOffTime);
    }
}
