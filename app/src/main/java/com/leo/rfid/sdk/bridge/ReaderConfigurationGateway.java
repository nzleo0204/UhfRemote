package com.leo.rfid.sdk.bridge;

import com.leo.rfid.sdk.model.*;

/**
 * 定义读写器参数读取与设置的底层能力。
 */
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
