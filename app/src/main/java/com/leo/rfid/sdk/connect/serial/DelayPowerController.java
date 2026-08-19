package com.leo.rfid.sdk.connect.serial;

import java.io.IOException;

/** 适用于模块已由系统上电，只需要等待初始化的默认控制器。 */
public final class DelayPowerController implements SerialPowerController {
    private final int delayMs;

    public DelayPowerController(int delayMs) {
        if (delayMs < 0) {
            throw new IllegalArgumentException("上电延时不能小于 0");
        }
        this.delayMs = delayMs;
    }

    @Override
    public void powerOn() throws IOException {
        // 模块由设备系统负责上电。
    }

    @Override
    public void powerOff() {
        // 不主动关闭设备电源。
    }

    @Override
    public int getDelayAfterPowerOn() {
        return delayMs;
    }
}
