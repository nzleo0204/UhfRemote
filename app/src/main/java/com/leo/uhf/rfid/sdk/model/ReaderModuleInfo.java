package com.leo.uhf.rfid.sdk.model;

/**
 * 保存读写器板卡与射频模块的身份和版本信息。
 */
public final class ReaderModuleInfo {
    public final ModuleSubtype subtype;
    public final int rawSubtype;
    public final String boardSerial;
    public final String boardVersion;
    public final String moduleSerial;
    public final String moduleVersion;

    public ReaderModuleInfo(ModuleSubtype subtype, int rawSubtype, String boardSerial,
            String boardVersion, String moduleSerial, String moduleVersion) {
        this.subtype = subtype;
        this.rawSubtype = rawSubtype;
        this.boardSerial = boardSerial;
        this.boardVersion = boardVersion;
        this.moduleSerial = moduleSerial;
        this.moduleVersion = moduleVersion;
    }
}
