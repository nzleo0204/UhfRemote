package com.leo.uhf.rfid.sdk.connection.serial;

import java.io.IOException;

/** 适用于模块已由系统上电，只需要等待初始化的默认控制器。 */
public final class DelayPowerController implements SerialPowerController {
    private final int delayMs;

    /**
     * 创建只等待、不主动控制电源的上电控制器。
     *
     * @param delayMs 上电后等待模块就绪的毫秒数
     */
    public DelayPowerController(int delayMs) {
        if (delayMs < 0) {
            throw new IllegalArgumentException("上电延时不能小于 0");
        }
        this.delayMs = delayMs;
    }

    /** 模块由设备系统负责上电，此处不执行额外操作。 */
    @Override
    public void powerOn() throws IOException {
        // 模块由设备系统负责上电。
    }

    /** 默认控制器不主动关闭设备电源。 */
    @Override
    public void powerOff() {
        // 不主动关闭设备电源。
    }

    /** 返回连接前需要等待的毫秒数。 */
    @Override
    public int getDelayAfterPowerOn() {
        return delayMs;
    }
}
