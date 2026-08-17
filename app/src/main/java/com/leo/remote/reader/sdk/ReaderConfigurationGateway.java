package com.leo.remote.reader.sdk;

import com.leo.remote.reader.model.*;


public interface ReaderConfigurationGateway {
    ReaderConfiguration readConfiguration(ModuleSubtype subtype) throws ReaderException;
    int setProtocol(TagProtocol protocol);
    int setPowerTenthsDbm(int powerTenthsDbm);
    int setBlfProfile(int profile);
    int setSession(ModuleSubtype subtype, int session, int target, int selected);
    int setInventoryArea(int area, int address, int wordLen);
    int[] getInventoryArea();
    Integer getPowerTenthsDbm();
    Integer getBlfProfile();
    int[] getQueryValues(ModuleSubtype subtype);
    ReaderQParams getQParams(ModuleSubtype subtype);
}
