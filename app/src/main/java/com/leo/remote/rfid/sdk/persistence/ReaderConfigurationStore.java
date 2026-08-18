package com.leo.remote.rfid.sdk.persistence;

import com.leo.remote.rfid.sdk.model.*;


public interface ReaderConfigurationStore {
    void saveConfiguration(ModuleSubtype subtype, ReaderConfiguration configuration);
    ReaderConfiguration loadConfiguration(ModuleSubtype subtype);
    void saveSelected(ModuleSubtype subtype, int selected);
    int loadSelected(ModuleSubtype subtype);
}
