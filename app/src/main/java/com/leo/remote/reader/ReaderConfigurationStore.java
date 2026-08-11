package com.leo.remote.reader;

interface ReaderConfigurationStore {
    void saveConfiguration(ModuleSubtype subtype, ReaderConfiguration configuration);
    ReaderConfiguration loadConfiguration(ModuleSubtype subtype);
    void saveSelected(ModuleSubtype subtype, int selected);
    int loadSelected(ModuleSubtype subtype);
}
