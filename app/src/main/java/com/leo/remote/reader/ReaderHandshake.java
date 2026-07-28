package com.leo.remote.reader;

final class ReaderHandshake {
    static final class Result {
        final ReaderModuleInfo moduleInfo;
        final ReaderConfiguration configuration;

        Result(ReaderModuleInfo moduleInfo, ReaderConfiguration configuration) {
            this.moduleInfo = moduleInfo;
            this.configuration = configuration;
        }
    }

    private ReaderHandshake() {}

    static Result perform(UhfSdkGateway gateway) throws ReaderException {
        ReaderModuleInfo info = gateway.readModuleInfo();
        if (info.subtype == ModuleSubtype.UNKNOWN) {
            throw new ReaderException("Unknown RM70XX subtype: " + info.rawSubtype, info.rawSubtype);
        }
        if (isBlank(info.boardSerial) || isBlank(info.boardVersion)
                || isBlank(info.moduleSerial) || isBlank(info.moduleVersion)) {
            throw new ReaderException("RM70XX device information is incomplete", -7);
        }
        int status = gateway.setProtocol(TagProtocol.ISO_18000_6C);
        if (status != 0) {
            throw new ReaderException("Unable to select 6C protocol", status);
        }
        status = gateway.configureDefaultInventory(TagProtocol.ISO_18000_6C);
        if (status != 0) {
            throw new ReaderException("Unable to configure inventory", status);
        }
        return new Result(info, gateway.readConfiguration(info.subtype));
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
