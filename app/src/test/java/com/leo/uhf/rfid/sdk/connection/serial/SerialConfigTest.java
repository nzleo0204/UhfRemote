package com.leo.uhf.rfid.sdk.connection.serial;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.leo.uhf.rfid.sdk.model.ModuleSubtype;

import org.junit.Test;

/** 验证串口连接配置的边界校验。 */
public class SerialConfigTest {
    @Test public void acceptsSupportedConfiguration() {
        SerialConfig config = new SerialConfig(" /dev/ttyS1 ", 115200,
                ModuleSubtype.RM610, 500);
        assertEquals("/dev/ttyS1", config.portPath);
        assertEquals(500, config.powerDelayMs);
    }

    @Test public void rejectsUnsupportedValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new SerialConfig("/dev/ttyS1", 12345, ModuleSubtype.R2000, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new SerialConfig("/dev/ttyS1", 115200, ModuleSubtype.UNKNOWN, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new SerialConfig("/dev/ttyS1", 115200, ModuleSubtype.R2000, -1));
    }
}
