package com.leo.rfid.sdk.connect.serial;

import java.io.IOException;

/** 抽象串口模块上电方式，由具体设备平台实现。 */
public interface SerialPowerController {
    /** 执行上电动作。 */
    void powerOn() throws IOException;

    /** 执行下电动作。 */
    void powerOff();

    /** 返回上电后等待模块就绪的毫秒数。 */
    int getDelayAfterPowerOn();
}
