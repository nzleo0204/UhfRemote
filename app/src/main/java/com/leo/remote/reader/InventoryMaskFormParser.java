package com.leo.remote.reader;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Pure validation and conversion for the shared inventory-mask form. */
public final class InventoryMaskFormParser {
    public enum Error {
        OFFSET_INVALID,
        LENGTH_INVALID,
        LENGTH_NOT_POSITIVE,
        HEX_REQUIRED,
        HEX_INVALID,
        DATA_TOO_LONG,
        LENGTH_EXCEEDS_DATA,
        SIX_B_LENGTH_NOT_BYTE_ALIGNED,
        OFFSET_OUT_OF_RANGE
    }

    public static final class Result {
        @Nullable private final InventoryMaskConfig config;
        @Nullable private final Error error;

        private Result(@Nullable InventoryMaskConfig config, @Nullable Error error) {
            this.config = config;
            this.error = error;
        }

        public boolean isSuccess() { return config != null; }
        @Nullable public InventoryMaskConfig getConfig() { return config; }
        @Nullable public Error getError() { return error; }
    }

    private InventoryMaskFormParser() {}

    @NonNull
    public static Result parse(@NonNull TagProtocol protocol, int selectedBank,
            @Nullable String offsetText, @Nullable String lengthText, @Nullable String hexText) {
        Integer offset = parseUnsigned(offsetText);
        if (offset == null) { return failure(Error.OFFSET_INVALID); }
        Integer length = parseUnsigned(lengthText);
        if (length == null) { return failure(Error.LENGTH_INVALID); }
        if (length == 0) { return failure(Error.LENGTH_NOT_POSITIVE); }

        String hex = hexText == null ? "" : hexText.trim();
        if (hex.isEmpty()) { return failure(Error.HEX_REQUIRED); }
        if ((hex.length() & 1) != 0 || !hex.matches("[0-9A-Fa-f]+")) {
            return failure(Error.HEX_INVALID);
        }
        byte[] mask = HexCodec.decode(hex);
        if (mask.length > 64) { return failure(Error.DATA_TOO_LONG); }
        if (length > mask.length * 8) { return failure(Error.LENGTH_EXCEEDS_DATA); }
        if (protocol == TagProtocol.ISO_18000_6B && (length & 7) != 0) {
            return failure(Error.SIX_B_LENGTH_NOT_BYTE_ALIGNED);
        }
        if ((protocol == TagProtocol.GJB_7377_1 || protocol == TagProtocol.GB_T_29768)
                && offset > 0xFF) {
            return failure(Error.OFFSET_OUT_OF_RANGE);
        }
        int bank = switch (protocol) {
            case ISO_18000_6C, GB_T_29768 -> selectedBank;
            case ISO_18000_6B -> 0;
            case GJB_7377_1 -> 1;
        };
        return new Result(new InventoryMaskConfig(bank, offset, length, mask), null);
    }

    @Nullable
    private static Integer parseUnsigned(@Nullable String value) {
        try {
            int parsed = Integer.parseInt(value == null ? "" : value.trim());
            return parsed < 0 ? null : parsed;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Result failure(Error error) { return new Result(null, error); }
}
