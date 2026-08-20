package com.leo.uhf.rfid.api.model;

import java.util.ArrayList;
import java.util.List;

/** Inventory area options defined by the UHF library protocol. */
public enum InventoryArea {
    C_EPC_ONLY(TagProtocol.ISO_18000_6C, 0, "仅盘点 EPC", "EPC 号"),
    C_EPC_USER(TagProtocol.ISO_18000_6C, 1, "盘点 EPC 和 USER", "EPC/USER"),
    C_EPC_TID(TagProtocol.ISO_18000_6C, 2, "盘点 EPC 和 TID", "EPC/TID"),
    C_EPC_RESERVED(TagProtocol.ISO_18000_6C, 3, "盘点 EPC 和 RESERVED", "EPC/RESERVED"),
    B_UID_ONLY(TagProtocol.ISO_18000_6B, 0, "仅盘点 UID", "UID"),
    B_UID_USER(TagProtocol.ISO_18000_6B, 1, "盘点 UID 和 USER", "UID/USER"),
    GJB_CODE_ONLY(TagProtocol.GJB_7377_1, 0, "仅盘点编码区", "编码区"),
    GJB_CODE_USER(TagProtocol.GJB_7377_1, 1, "盘点编码区和用户区", "编码区/用户区"),
    GJB_CODE_INFO(TagProtocol.GJB_7377_1, 2, "盘点编码区和标签信息区", "编码区/信息区"),
    GB_CODE_ONLY(TagProtocol.GB_T_29768, 0, "仅盘点编码区", "编码区"),
    GB_CODE_USER(TagProtocol.GB_T_29768, 1, "盘点编码区和用户区", "编码区/用户区"),
    GB_CODE_INFO(TagProtocol.GB_T_29768, 2, "盘点编码区和标签信息区", "编码区/信息区");

    private final TagProtocol protocol;
    private final int value;
    private final String displayName;
    private final String columnHeader;

    InventoryArea(TagProtocol protocol, int value, String displayName, String columnHeader) {
        this.protocol = protocol;
        this.value = value;
        this.displayName = displayName;
        this.columnHeader = columnHeader;
    }

    public TagProtocol getProtocol() { return protocol; }
    public int getValue() { return value; }
    public String getDisplayName() { return displayName; }
    public String getColumnHeader() { return columnHeader; }
    public boolean isBaseOnly() { return value == 0; }

    public static List<InventoryArea> forProtocol(TagProtocol protocol) {
        List<InventoryArea> result = new ArrayList<>();
        for (InventoryArea area : values()) {
            if (area.protocol == protocol) { result.add(area); }
        }
        return result;
    }

    public static InventoryArea of(TagProtocol protocol, int value) {
        for (InventoryArea area : values()) {
            if (area.protocol == protocol && area.value == value) { return area; }
        }
        return forProtocol(protocol).get(0);
    }
}
