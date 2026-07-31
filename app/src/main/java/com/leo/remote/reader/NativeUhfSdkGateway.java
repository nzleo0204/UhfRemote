package com.leo.remote.reader;

import com.uhf.linkage.Linkage;
import com.uhf.structures.DynamicQParams;
import com.uhf.structures.FixedQParams;
import com.uhf.structures.InventoryData;
import com.uhf.structures.InventoryParams;
import com.uhf.structures.Parameters;
import com.uhf.structures.RW_Params;
import com.uhf.structures.Rfid_Value;
import com.uhf.structures.Select6BCriteria;
import com.uhf.structures.SelectCriteria;
import com.uhf.structures.TagGroup;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class NativeUhfSdkGateway implements UhfSdkGateway {
    private static final int STATUS_OK = 0;
    private final Linkage linkage = new Linkage();

    @Override
    public int initialize() { return linkage.initRFID(); }

    @Override
    public void deinitialize() { linkage.deinitRFID(); }

    @Override
    public void useRm70xx() { linkage.setRFModuleType(2); }

    @Override
    public void setTransport(TransportType transport) {
        linkage.setRFConnectMode(transport == TransportType.WIFI ? 1 : 2);
    }

    @Override
    public int connectNetwork(String address, int port) { return linkage.connectRemoteNetwork(address, port); }

    @Override
    public int closeNetwork() { return linkage.closeNetwork(); }

    @Override
    public void setOutboundDataListener(OutboundDataListener listener) {
        linkage.setOnBluetoothListener(data -> {
            if (listener != null) {
                listener.onOutboundData(data);
            }
        });
    }

    @Override
    public void pushRemoteData(byte[] data) { linkage.pushRemoteRFIDData(data); }

    @Override
    public ReaderModuleInfo readModuleInfo() throws ReaderException {
        String boardSerial = readString(linkage::getBoardSerialNumber, "getBoardSerialNumber");
        String boardVersion = readString(linkage::getBoardSoftVersion, "getBoardSoftVersion");
        String moduleSerial = readString(linkage::getSerialNumber, "getSerialNumber");
        String moduleVersion = readString(linkage::getVersion, "getVersion");
        Rfid_Value subtypeValue = new Rfid_Value();
        check(linkage.getBoardModuleType(subtypeValue), "getBoardModuleType");
        ModuleSubtype subtype = ModuleSubtype.fromRawValue(subtypeValue.value);
        if (subtype == ModuleSubtype.UNKNOWN) {
            throw new ReaderException("Unknown RM70XX subtype: " + subtypeValue.value, subtypeValue.value);
        }
        return new ReaderModuleInfo(subtype, subtypeValue.value, boardSerial, boardVersion,
                moduleSerial, moduleVersion);
    }

    @Override
    public int setProtocol(TagProtocol protocol) { return linkage.setTagType(protocol.getSdkValue()); }

    @Override
    public int configureDefaultInventory(TagProtocol protocol) {
        InventoryParams params = new InventoryParams();
        if (protocol == TagProtocol.ISO_18000_6C) {
            params.setValue(1, 0, 6);
        } else if (protocol == TagProtocol.ISO_18000_6B) {
            params.setValue(0, 0, 8);
        } else {
            params.setValue(0, 0, 6);
        }
        return linkage.Radio_SetInventoryParams(params);
    }

    @Override
    public int startInventory(int mode, int maskFlag) { return linkage.startInventory(mode, maskFlag); }

    @Override
    public int stopInventory() { return linkage.stopInventory(); }

    @Override
    public void setInventoryListener(InventoryListener listener) {
        linkage.setOnInventoryListener(data -> {
            if (listener != null && data != null) {
                listener.onTag(toReaderTag(data));
            }
        });
    }

    @Override
    public ReaderTag inventoryOnce(int timeoutMs) throws ReaderException {
        InventoryData data = new InventoryData();
        check(linkage.inventoryOnceSync(0, timeoutMs, data), "inventoryOnceSync");
        return toReaderTag(data);
    }

    @Override
    public ReaderConfiguration readConfiguration(ModuleSubtype subtype) throws ReaderException {
        Rfid_Value power = new Rfid_Value();
        check(linkage.Radio_GetAntennaPower(power), "Radio_GetAntennaPower");
        if (subtype == ModuleSubtype.MAGIC_RF) {
            Parameters params = new Parameters();
            check(linkage.get_Query(params), "get_Query");
            return new ReaderConfiguration(power.value, 1, 0, params.getSession(),
                    params.getTarget(), false, params.getQ());
        }
        Rfid_Value profile = new Rfid_Value();
        TagGroup group = new TagGroup();
        Rfid_Value algorithm = new Rfid_Value();
        check(linkage.Radio_GetCurrentLinkProfile(profile), "Radio_GetCurrentLinkProfile");
        check(linkage.Radio_GetQueryTagGroup(group), "Radio_GetQueryTagGroup");
        check(linkage.Radio_getCurrentSingulationAlgorithm(algorithm), "Radio_getCurrentSingulationAlgorithm");
        int q;
        if (algorithm.value == 1) {
            DynamicQParams params = new DynamicQParams();
            check(linkage.Radio_GetSingulationAlgorithmDyParameters(params), "Radio_GetSingulationAlgorithmDyParameters");
            q = params.startQValue;
        } else {
            FixedQParams params = new FixedQParams();
            check(linkage.Radio_GetSingulationAlgorithmFixedParameters(params), "Radio_GetSingulationAlgorithmFixedParameters");
            q = params.qValue;
        }
        return new ReaderConfiguration(power.value, 1, profile.value, group.session,
                group.target, algorithm.value == 1, q);
    }

    @Override
    public int setPowerTenthsDbm(int powerTenthsDbm) {
        return linkage.Radio_SetAntennaPower(powerTenthsDbm);
    }

    @Override
    public int setBlfProfile(int profile) { return linkage.Radio_SetCurrentLinkProfile(profile); }

    @Override
    public int setQueryGroup(int session, int target) {
        TagGroup current = new TagGroup();
        int status = linkage.Radio_GetQueryTagGroup(current);
        if (status != STATUS_OK) { return status; }
        current.session = session;
        current.target = target;
        return linkage.Radio_SetQueryTagGroup(current);
    }

    @Override
    public int setQ(boolean dynamic, int qValue) {
        int status = linkage.Radio_setCurrentSingulationAlgorithm(dynamic ? 1 : 0);
        if (status != STATUS_OK) { return status; }
        if (dynamic) {
            return linkage.Radio_SetSingulationAlgorithmDyParameters(
                    new DynamicQParams(qValue, 0, 15, 0, 1, 1));
        }
        return linkage.Radio_SetSingulationAlgorithmFixedParameters(
                new FixedQParams(qValue, 0, 1, 0));
    }

    @Override
    public int setMagicQuery(int session, int target, int qValue) {
        Parameters current = new Parameters();
        int status = linkage.get_Query(current);
        if (status != STATUS_OK) { return status; }
        return linkage.set_Query(current.getDR(), current.getM(), current.getTRext(),
                current.getSel(), session, target, qValue);
    }

    @Override
    public int applyInventoryMask(TagProtocol protocol, InventoryMaskConfig config) {
        byte[] mask = config.getMask();
        if (protocol == TagProtocol.ISO_18000_6B) {
            Select6BCriteria criteria = new Select6BCriteria();
            criteria.status = 1;
            int byteLength = (config.lengthBits + 7) / 8;
            criteria.length = byteLength;
            System.arraycopy(mask, 0, criteria.maskData, 0,
                    Math.min(byteLength, criteria.maskData.length));
            return linkage.set18K6BSelectCriteria(criteria);
        }
        SelectCriteria criteria = new SelectCriteria();
        criteria.selectorIdx = 0;
        criteria.status = 1;
        criteria.bank = config.bank;
        criteria.offset = ProtocolEncoding.encodeMaskOffset(protocol, config.offsetBits);
        criteria.length = config.lengthBits;
        criteria.session = 4;
        criteria.action = 0;
        criteria.maskData = Arrays.copyOf(mask, Math.max(64, mask.length));
        return linkage.set18K6CSelectCriteria(criteria);
    }

    @Override
    public int clearInventoryMask(TagProtocol protocol) {
        return clearTargetMask(protocol);
    }

    @Override
    public int setTargetMask(TagProtocol protocol, ReaderTag tag) {
        byte[] id = HexCodec.decode(tag.id);
        if (protocol == TagProtocol.ISO_18000_6B) {
            Select6BCriteria criteria = new Select6BCriteria();
            criteria.status = 1;
            criteria.length = id.length;
            System.arraycopy(id, 0, criteria.maskData, 0, Math.min(id.length, criteria.maskData.length));
            return linkage.set18K6BSelectCriteria(criteria);
        }
        SelectCriteria criteria = new SelectCriteria();
        criteria.selectorIdx = 0;
        criteria.status = 1;
        criteria.bank = ProtocolEncoding.targetMaskBank(protocol);
        criteria.offset = ProtocolEncoding.targetMaskOffset(protocol);
        criteria.length = id.length * 8;
        criteria.session = 4;
        criteria.action = 0;
        criteria.maskData = Arrays.copyOf(id, Math.max(64, id.length));
        return linkage.set18K6CSelectCriteria(criteria);
    }

    @Override
    public int clearTargetMask(TagProtocol protocol) {
        if (protocol == TagProtocol.ISO_18000_6B) {
            return linkage.set18K6BSelectCriteria(new Select6BCriteria(0));
        }
        return linkage.set18K6CSelectCriteria(new SelectCriteria(0));
    }

    @Override
    public byte[] readTag(TagProtocol protocol, int length, int address, int bank, byte[] password,
            int timeoutMs) throws ReaderException {
        RW_Params result = new RW_Params();
        check(linkage.Radio_readTagSync(length, address, bank, password, timeoutMs, result), "Radio_readTagSync");
        if (result.status != STATUS_OK) {
            throw new ReaderException("Radio_readTagSync result", result.status);
        }
        return result.ReadData == null ? new byte[0] : Arrays.copyOf(result.ReadData, Math.min(result.DataLen, result.ReadData.length));
    }

    @Override
    public int writeTag(TagProtocol protocol, int length, int address, int bank, byte[] password,
            byte[] data, int timeoutMs) {
        RW_Params result = new RW_Params();
        int status = protocol == TagProtocol.GJB_7377_1 || protocol == TagProtocol.GB_T_29768
                ? linkage.Radio_BlockWriteTagSync(length, address, bank, password, data, timeoutMs, result)
                : linkage.Radio_WriteTagSync(length, address, bank, password, data, timeoutMs, result);
        return status == STATUS_OK ? result.status : status;
    }

    @Override
    public int lockTag(byte[] password, int bank, int policy, int timeoutMs) {
        int[] values = {4, 4, 4, 4, 4};
        if (bank < 0 || bank >= values.length) { return -1; }
        values[bank] = policy;
        RW_Params result = new RW_Params();
        int status = linkage.Radio_lockTagSync(password, values[0], values[1], values[2],
                values[3], values[4], timeoutMs, result);
        return status == STATUS_OK ? result.status : status;
    }

    @Override
    public int killTag(byte[] accessPassword, byte[] killPassword, int timeoutMs) {
        RW_Params result = new RW_Params();
        int status = linkage.Radio_KillTagSync(accessPassword, killPassword, timeoutMs, result);
        return status == STATUS_OK ? result.status : status;
    }

    private static ReaderTag toReaderTag(InventoryData data) {
        String id = HexCodec.encode(data.getEpc(), data.getEpcLength());
        String extra = HexCodec.encode(data.getData(), data.getDataLength());
        return new ReaderTag(id, extra, data.getRSSI(), data.getTagType(), data.getTagInventoriedTimes());
    }

    private String readString(ValueReader reader, String operation) throws ReaderException {
        Rfid_Value status = new Rfid_Value();
        byte[] value = reader.read(status);
        check(status.value, operation);
        return value == null ? "" : new String(value, StandardCharsets.UTF_8).trim();
    }

    private static void check(int status, String operation) throws ReaderException {
        if (status != STATUS_OK) {
            throw new ReaderException(operation + " failed", status);
        }
    }

    private interface ValueReader { byte[] read(Rfid_Value value); }
}
