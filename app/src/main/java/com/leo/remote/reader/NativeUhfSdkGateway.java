package com.leo.remote.reader;

import android.annotation.SuppressLint;
import android.util.Log;

import com.uhf.linkage.Linkage;
import com.uhf.structures.AntennaPorts;
import com.uhf.structures.DynamicQParams;
import com.uhf.structures.FixedQParams;
import com.uhf.structures.InventoryData;
import com.uhf.structures.InventoryParams;
import com.uhf.structures.LowpowerParams;
import com.uhf.structures.Parameters;
import com.uhf.structures.RW_Params;
import com.uhf.structures.Rfid_Value;
import com.uhf.structures.Select6BCriteria;
import com.uhf.structures.SelectCriteria;
import com.uhf.structures.TagGroup;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@SuppressLint("LogNotTimber")
public final class NativeUhfSdkGateway implements UhfSdkGateway {
    private static final String TAG = "UhfReader";
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
    public int applyInventoryParams(TagProtocol protocol, int area, int address, int wordLen) {
        return setInventoryArea(area, address, wordLen);
    }

    @Override
    public int setInventoryArea(int area, int address, int wordLen) {
        InventoryParams params = new InventoryParams();
        params.setValue(area, address, wordLen);
        return linkage.Radio_SetInventoryParams(params);
    }

    @Override
    public int[] getInventoryArea() {
        InventoryParams params = new InventoryParams();
        if (linkage.Radio_GetInventoryParams(params) != STATUS_OK) { return null; }
        return new int[]{params.inventoryArea, params.address, params.len};
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
    public void setInventoryStopListener(InventoryStopListener listener) {
        linkage.setOnInventoryStopListener(status -> {
            if (listener != null) {
                listener.onInventoryStopped(status);
            }
        });
    }

    @Override
    public int setLowPowerScheduler(int highPerformanceTime, int inventoryOnTime,
            int inventoryOffTime) {
        AntennaPorts antenna = new AntennaPorts();
        int status = linkage.getAntennaPort(0, antenna);
        if (status != STATUS_OK) {
            return status;
        }
        LowpowerParams params = new LowpowerParams();
        prepareLowPowerValues(antenna, params, highPerformanceTime, inventoryOnTime,
                inventoryOffTime);
        status = linkage.setAntennaPort(0, antenna.antennaStatus, antenna.powerLevel,
                antenna.dwellTime, antenna.numberInventoryCycles);
        if (status != STATUS_OK) {
            return status;
        }
        return linkage.setLowpowerScheduler(params);
    }

    static void prepareLowPowerValues(AntennaPorts antenna, LowpowerParams params,
            int highPerformanceTime, int inventoryOnTime, int inventoryOffTime) {
        antenna.setDwellTime(inventoryOnTime);
        params.setHighPerformanceTime(highPerformanceTime);
        params.setInventoryOnTime(inventoryOnTime);
        params.setInventoryOffTime(inventoryOffTime);
    }

    @Override
    public ReaderTag inventoryOnce(int timeoutMs) throws ReaderException {
        InventoryData data = new InventoryData();
        check(linkage.inventoryOnceSync(0, timeoutMs, data), "inventoryOnceSync");
        return toReaderTag(data);
    }

    @Override
    public ReaderConfiguration readConfiguration(ModuleSubtype subtype) throws ReaderException {
        Integer power = getPowerTenthsDbm();
        Integer blf = subtype == ModuleSubtype.RM8011 ? 0 : getBlfProfile();
        int[] group = getQueryValues(subtype);
        ReaderQParams q = getQParams(subtype);
        if (power == null || blf == null || group == null || q == null) {
            throw new ReaderException("Unable to read reader configuration", -8);
        }
        ReaderConfiguration cached = new ReaderConfigCache().loadConfiguration(subtype);
        int mode = cached == null ? ReaderConfigCache.getDefaultConfiguration(subtype).inventoryMode
                : cached.inventoryMode;
        return new ReaderConfiguration(power, mode, blf, group[0], group[1], q.dynamic,
                q.qValue, q.minQ, q.maxQ, q.retryCount, q.thresholdMultiplier,
                q.toggleTarget, q.repeatUntilNoTags);
    }

    @Override
    public Integer getPowerTenthsDbm() {
        Rfid_Value power = new Rfid_Value();
        return linkage.Radio_GetAntennaPower(power) == STATUS_OK ? power.value : null;
    }

    @Override
    public Integer getBlfProfile() {
        Rfid_Value profile = new Rfid_Value();
        return linkage.Radio_GetCurrentLinkProfile(profile) == STATUS_OK ? profile.value : null;
    }

    @Override
    public int[] getQueryValues(ModuleSubtype subtype) {
        if (subtype == ModuleSubtype.RM8011) {
            Parameters params = new Parameters();
            if (linkage.get_Query(params) != STATUS_OK) { return null; }
            return new int[]{params.getSession(), params.getTarget(), params.getSel()};
        }
        TagGroup group = new TagGroup();
        if (linkage.Radio_GetQueryTagGroup(group) != STATUS_OK) { return null; }
        return new int[]{group.session, group.target, group.selected};
    }

    @Override
    public ReaderQParams getQParams(ModuleSubtype subtype) {
        if (subtype == ModuleSubtype.RM8011) {
            Parameters params = new Parameters();
            if (linkage.get_Query(params) != STATUS_OK) { return null; }
            return ReaderQParams.fixed(params.getQ(), 0, 1, 0);
        }
        Rfid_Value algorithm = new Rfid_Value();
        if (linkage.Radio_getCurrentSingulationAlgorithm(algorithm) != STATUS_OK) {
            return null;
        }
        if (algorithm.value == 1) {
            DynamicQParams params = new DynamicQParams();
            if (linkage.Radio_GetSingulationAlgorithmDyParameters(params) != STATUS_OK) {
                return null;
            }
            return ReaderQParams.dynamic(params.startQValue, params.minQValue, params.maxQValue,
                    params.retryCount, params.thresholdMultiplier, params.toggleTarget);
        }
        FixedQParams params = new FixedQParams();
        if (linkage.Radio_GetSingulationAlgorithmFixedParameters(params) != STATUS_OK) {
            return null;
        }
        return ReaderQParams.fixed(params.qValue, params.retryCount, params.toggleTarget,
                params.repeatUntiNoTags);
    }

    @Override
    public int setPowerTenthsDbm(int powerTenthsDbm) {
        return linkage.Radio_SetAntennaPower(powerTenthsDbm);
    }

    @Override
    public int setBlfProfile(int profile) { return linkage.Radio_SetCurrentLinkProfile(profile); }

    @Override
    public int setSession(ModuleSubtype subtype, int session, int target, int selected) {
        if (subtype == ModuleSubtype.RM8011) {
            Parameters current = new Parameters();
            int status = linkage.get_Query(current);
            if (status != STATUS_OK) { return status; }
            return linkage.set_Query(current.getDR(), current.getM(), current.getTRext(),
                    selected, session, target, current.getQ());
        }
        TagGroup current = new TagGroup();
        int status = linkage.Radio_GetQueryTagGroup(current);
        if (status != STATUS_OK) { return status; }
        current.session = session;
        current.target = target;
        current.selected = selected;
        return linkage.Radio_SetQueryTagGroup(current);
    }

    @Override
    public int setQ(boolean dynamic, int qValue, int minQValue, int maxQValue,
            int retryCount, int thresholdMultiplier, int toggleTarget, int repeatUntilNoTags) {
        int status = linkage.Radio_setCurrentSingulationAlgorithm(dynamic ? 1 : 0);
        if (status != STATUS_OK) { return status; }
        if (dynamic) {
            DynamicQParams params = new DynamicQParams();
            params.setValue(qValue, minQValue, maxQValue, retryCount, toggleTarget,
                    thresholdMultiplier);
            return linkage.Radio_SetSingulationAlgorithmDyParameters(params);
        }
        FixedQParams params = new FixedQParams();
        params.setValue(qValue, retryCount, toggleTarget, repeatUntilNoTags);
        return linkage.Radio_SetSingulationAlgorithmFixedParameters(params);
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
    public int applyInventoryMask(TagProtocol protocol, ModuleSubtype subtype,
            InventoryMaskConfig config) {
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
        int status = setSelectValue(subtype, 2);
        if (status != STATUS_OK) {
            return status;
        }
        SelectCriteria criteria = new SelectCriteria();
        status = linkage.get18K6CSelectCriteria(criteria);
        if (status != STATUS_OK) {
            return status;
        }
        criteria.selectorIdx = 0;
        criteria.status = 1;
        criteria.bank = config.bank;
        criteria.offset = ProtocolEncoding.encodeMaskOffset(protocol, config.offsetBits);
        criteria.length = config.lengthBits;
        criteria.session = 4;
        criteria.jq = 0;
        criteria.action = 0;
        criteria.maskData = toFixedMaskData(mask);
        return linkage.set18K6CSelectCriteria(criteria);
    }

    @Override
    public int clearInventoryMask(TagProtocol protocol, ModuleSubtype subtype, int selected) {
        return clearTargetMask(protocol, subtype, selected);
    }

    @Override
    public int setTargetMask(TagProtocol protocol, ModuleSubtype subtype, ReaderTag tag) {
        byte[] id = HexCodec.decode(tag.id);
        if (protocol == TagProtocol.ISO_18000_6B) {
            Select6BCriteria criteria = new Select6BCriteria();
            criteria.status = 1;
            criteria.length = id.length;
            System.arraycopy(id, 0, criteria.maskData, 0, Math.min(id.length, criteria.maskData.length));
            return linkage.set18K6BSelectCriteria(criteria);
        }
        int status = setSelectValue(subtype, 2);
        if (status != STATUS_OK) {
            return status;
        }
        SelectCriteria criteria = new SelectCriteria();
        status = linkage.get18K6CSelectCriteria(criteria);
        if (status != STATUS_OK) {
            return status;
        }
        criteria.selectorIdx = 0;
        criteria.status = 1;
        criteria.bank = ProtocolEncoding.targetMaskBank(protocol);
        criteria.offset = ProtocolEncoding.targetMaskOffset(protocol);
        criteria.length = id.length * 8;
        criteria.session = 4;
        criteria.jq = 0;
        criteria.action = 0;
        criteria.maskData = toFixedMaskData(id);
        return linkage.set18K6CSelectCriteria(criteria);
    }

    @Override
    public int clearTargetMask(TagProtocol protocol, ModuleSubtype subtype, int selected) {
        if (protocol == TagProtocol.ISO_18000_6B) {
            return linkage.set18K6BSelectCriteria(new Select6BCriteria(0));
        }
        SelectCriteria criteria = new SelectCriteria();
        int status = linkage.get18K6CSelectCriteria(criteria);
        if (status != STATUS_OK) {
            return status;
        }
        criteria.selectorIdx = 0;
        criteria.status = 0;
        criteria.bank = 0;
        criteria.offset = 0;
        criteria.length = 0;
        criteria.session = 0;
        criteria.jq = 0;
        criteria.action = 0;
        criteria.maskData = new byte[64];
        status = linkage.set18K6CSelectCriteria(criteria);
        return status == STATUS_OK ? setSelectValue(subtype, selected) : status;
    }

    /** 以指定 Sel 值更新 Query，保留其他 Query 参数。 */
    private int setSelectValue(ModuleSubtype subtype, int selected) {
        if (subtype == ModuleSubtype.RM8011) {
            Parameters current = new Parameters();
            int status = linkage.get_Query(current);
            if (status != STATUS_OK) {
                return status;
            }
            return linkage.set_Query(current.getDR(), current.getM(), current.getTRext(),
                    selected, current.getSession(), current.getTarget(), current.getQ());
        }
        TagGroup tagGroup = new TagGroup();
        int status = linkage.Radio_GetQueryTagGroup(tagGroup);
        if (status != STATUS_OK) {
            return status;
        }
        tagGroup.selected = selected;
        return linkage.Radio_SetQueryTagGroup(tagGroup);
    }

    private static byte[] toFixedMaskData(byte[] mask) {
        byte[] data = new byte[64];
        if (mask != null) {
            System.arraycopy(mask, 0, data, 0, Math.min(mask.length, data.length));
        }
        return data;
    }

    @Override
    public TagReadResult readTag(TagProtocol protocol, int length, int address, int bank,
            byte[] password, int timeoutMs) throws ReaderException {
        RW_Params result = new RW_Params();
        check(linkage.Radio_readTagSync(length, address, bank, password, timeoutMs, result), "Radio_readTagSync");
        if (result.status != STATUS_OK) {
            throw new ReaderException("Radio_readTagSync result", result.status);
        }
        byte[] data = result.ReadData == null ? new byte[0]
                : Arrays.copyOf(result.ReadData, Math.min(result.DataLen, result.ReadData.length));
        byte[] epc = result.EPCData == null ? new byte[0]
                : Arrays.copyOf(result.EPCData, Math.min(result.EPCLen, result.EPCData.length));
        String chipModel = result.chipModel != null ? result.chipModel : "";
        int tidPrefix = result.tidPrefix;
        return new TagReadResult(data, epc, result.RSS, chipModel, tidPrefix);
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
        Log.d(TAG, "inventory tag epcLength=" + data.getEpcLength()
                + " dataLength=" + data.getDataLength()
                + " rssi=" + data.getRSSI()
                + " tagType=" + data.getTagType()
                + " inventoriedTimes=" + data.getTagInventoriedTimes());
        String id = HexCodec.encode(data.getEpc(), data.getEpcLength());
        String extra = HexCodec.encode(data.getData(), data.getDataLength());
        return new ReaderTag(id, extra, data.getRSSI(), data.getTagType(),
                data.getTagInventoriedTimes(), data.getChipModel(), data.getTidPrefix());
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
