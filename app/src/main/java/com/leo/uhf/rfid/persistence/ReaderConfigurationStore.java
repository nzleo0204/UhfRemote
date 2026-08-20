package com.leo.uhf.rfid.persistence;

import com.leo.uhf.rfid.api.model.*;

/**
 * 定义读写器运行参数缓存的读写接口。
 */
public interface ReaderConfigurationStore {
    void saveConfiguration(ModuleSubtype subtype, ReaderConfiguration configuration);
    ReaderConfiguration loadConfiguration(ModuleSubtype subtype);
    void saveSelected(ModuleSubtype subtype, int selected);
    int loadSelected(ModuleSubtype subtype);
}
