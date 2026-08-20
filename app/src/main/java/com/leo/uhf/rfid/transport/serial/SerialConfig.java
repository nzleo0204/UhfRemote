package com.leo.uhf.rfid.transport.serial;

import com.leo.uhf.rfid.api.model.ModuleSubtype;

import java.util.Arrays;

/** 保存串口连接所需的端口、波特率、模块型号和上电延时。 */
public final class SerialConfig {
    public static final String DEFAULT_PORT_PATH = "/dev/ttyS1";
    public static final int DEFAULT_BAUD_RATE = 115200;
    public static final int DEFAULT_POWER_DELAY_MS = 500;
    /** Linkage.open_serial(String) 当前不接受波特率，生产适配器固定使用 115200。 */
    private static final int[] SUPPORTED_BAUD_RATES = {115200};

    public final String portPath;
    public final int baudRate;
    public final ModuleSubtype moduleSubtype;
    public final int powerDelayMs;

    public SerialConfig(String portPath, int baudRate, ModuleSubtype moduleSubtype,
            int powerDelayMs) {
        if (portPath == null || portPath.trim().isEmpty()) {
            throw new IllegalArgumentException("串口路径不能为空");
        }
        if (Arrays.stream(SUPPORTED_BAUD_RATES).noneMatch(value -> value == baudRate)) {
            throw new IllegalArgumentException("不支持的波特率: " + baudRate);
        }
        if (moduleSubtype == null || moduleSubtype == ModuleSubtype.UNKNOWN) {
            throw new IllegalArgumentException("串口模块型号无效");
        }
        if (powerDelayMs < 0) {
            throw new IllegalArgumentException("上电延时不能小于 0");
        }
        this.portPath = portPath.trim();
        this.baudRate = baudRate;
        this.moduleSubtype = moduleSubtype;
        this.powerDelayMs = powerDelayMs;
    }

    /** 返回界面可选择的标准波特率列表。 */
    public static int[] supportedBaudRates() {
        return SUPPORTED_BAUD_RATES.clone();
    }

    /** 返回适合大多数平台的首次连接默认值。 */
    public static SerialConfig defaults() {
        return defaultsFor(ModuleSubtype.R2000);
    }

    /** 返回指定模块的默认串口配置，旗连模块默认等待 3000 毫秒。 */
    public static SerialConfig defaultsFor(ModuleSubtype subtype) {
        if (subtype == null || subtype == ModuleSubtype.UNKNOWN) {
            throw new IllegalArgumentException("串口模块型号无效");
        }
        return new SerialConfig(DEFAULT_PORT_PATH, DEFAULT_BAUD_RATE, subtype,
                subtype.getDefaultSerialReadyDelayMs());
    }
}
