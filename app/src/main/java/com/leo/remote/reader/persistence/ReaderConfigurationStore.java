package com.leo.remote.reader.persistence;

import com.leo.remote.reader.model.*;


public interface ReaderConfigurationStore {
    void saveConfiguration(ModuleSubtype subtype, ReaderConfiguration configuration);
    ReaderConfiguration loadConfiguration(ModuleSubtype subtype);
    void saveSelected(ModuleSubtype subtype, int selected);
    int loadSelected(ModuleSubtype subtype);
}
