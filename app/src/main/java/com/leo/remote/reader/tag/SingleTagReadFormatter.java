package com.leo.remote.reader.tag;

import com.leo.remote.reader.model.*;


import androidx.annotation.NonNull;

/** Pure defaults and presentation rules for single-tag reads. */
public final class SingleTagReadFormatter {
    public static final class Presentation {
        public final String bankLabel;
        public final String dataHex;
        public final String fullEpcHex;
        public final String chipModel;
        public final int rssi;

        private Presentation(String bankLabel, String dataHex, String fullEpcHex,
                String chipModel, int rssi) {
            this.bankLabel = bankLabel;
            this.dataHex = dataHex;
            this.fullEpcHex = fullEpcHex;
            this.chipModel = chipModel;
            this.rssi = rssi;
        }
    }

    private SingleTagReadFormatter() {}

    public static int defaultLength(@NonNull TagProtocol protocol, int bankPosition) {
        if (protocol == TagProtocol.ISO_18000_6C) {
            return switch (bankPosition) {
                case 0 -> 2;
                case 1, 2 -> 6;
                case 3 -> 8;
                default -> 0;
            };
        }
        return protocol == TagProtocol.ISO_18000_6B ? 8 : 4;
    }

    @NonNull
    public static Presentation format(@NonNull TagReadResult result,
            @NonNull TagProtocol protocol, int bankPosition, int requestedLength) {
        byte[] data = result.getData();
        byte[] epc = result.getEpc();
        String dataHex = limitHex(HexCodec.encode(data, data.length), requestedLength, protocol);
        String fullEpcHex = HexCodec.encode(epc, epc.length);
        boolean epcBank = protocol == TagProtocol.ISO_18000_6C && bankPosition == 1;
        boolean tidBank = protocol == TagProtocol.ISO_18000_6C && bankPosition == 2;
        if (dataHex.isEmpty() && epcBank) {
            dataHex = limitHex(fullEpcHex, requestedLength, protocol);
        }
        String chipModel = "";
        if (tidBank) {
            chipModel = result.getChipModel();
            if (chipModel.isEmpty()) { chipModel = ChipModelFormatter.formatFromTid(dataHex); }
        }
        return new Presentation(bankLabel(protocol, bankPosition), dataHex, fullEpcHex,
                chipModel, result.getRssi());
    }

    static String limitHex(String hex, int length, TagProtocol protocol) {
        int maxBytes = protocol == TagProtocol.ISO_18000_6B ? length : length * 2;
        int maxCharacters = Math.max(0, maxBytes * 2);
        return hex.length() <= maxCharacters ? hex : hex.substring(0, maxCharacters);
    }

    private static String bankLabel(TagProtocol protocol, int bankPosition) {
        if (protocol != TagProtocol.ISO_18000_6C) { return "数据"; }
        return switch (bankPosition) {
            case 0 -> "Reserved";
            case 1 -> "EPC";
            case 2 -> "TID";
            case 3 -> "USER";
            default -> "数据";
        };
    }
}
