package com.leo.remote.reader;

import static org.junit.Assert.assertEquals;

import com.uhf.structures.AntennaPorts;
import com.uhf.structures.LowpowerParams;
import org.junit.Test;

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
