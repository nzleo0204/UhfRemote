package com.leo.uhf.rfid.sdk.connection.serial;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** 验证默认串口上电控制器只提供等待时长。 */
public class DelayPowerControllerTest {
    @Test public void exposesConfiguredDelay() throws Exception {
        DelayPowerController controller = new DelayPowerController(500);
        controller.powerOn();
        assertEquals(500, controller.getDelayAfterPowerOn());
        controller.powerOff();
    }
}
