package com.leo.uhf.rfid.demo.inventory;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.hjq.base.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.leo.uhf.R;
import com.leo.uhf.rfid.api.model.InventoryArea;
import com.leo.uhf.rfid.api.model.InventoryItem;
import com.leo.uhf.rfid.api.model.TagProtocol;

/** Displays complete inventory values and lets the user reuse them as a mask. */
@SuppressLint("InflateParams")
public final class InventoryDetailSheet {

    public interface Listener {
        void onFillMask(int bank, String value);
    }

    private final BottomSheetDialog dialog;

    public InventoryDetailSheet(@NonNull Context context, @NonNull InventoryItem item,
            @NonNull InventoryArea area, @NonNull Listener listener) {
        dialog = new BottomSheetDialog(context);
        View content = LayoutInflater.from(context).inflate(R.layout.inventory_detail_sheet,
                null, false);
        dialog.setContentView(content);

        setText(content, R.id.tv_inventory_detail_id_label, idLabel(area));
        setText(content, R.id.tv_inventory_detail_id, item.getId());
        setText(content, R.id.tv_inventory_detail_data_label, dataLabel(area));
        setText(content, R.id.tv_inventory_detail_data, item.getData());
        setText(content, R.id.tv_inventory_detail_chip, item.getChipModel());
        setText(content, R.id.tv_inventory_detail_count,
                context.getString(R.string.inventory_detail_count, item.getCount()));
        setText(content, R.id.tv_inventory_detail_rssi,
                context.getString(R.string.inventory_detail_rssi, item.getRssi()));

        content.findViewById(R.id.row_inventory_detail_data).setVisibility(
                item.getData().isEmpty() ? View.GONE : View.VISIBLE);
        content.findViewById(R.id.row_inventory_detail_chip).setVisibility(
                item.getChipModel().isEmpty() ? View.GONE : View.VISIBLE);
        content.findViewById(R.id.row_inventory_detail_rssi).setVisibility(
                item.getRssi() == 0 ? View.GONE : View.VISIBLE);

        MaterialButton fillId = content.findViewById(R.id.btn_inventory_detail_fill_id);
        fillId.setOnClickListener(view -> {
            listener.onFillMask(idBank(area), item.getId());
            dialog.dismiss();
        });

        MaterialButton fillData = content.findViewById(R.id.btn_inventory_detail_fill_data);
        int secondaryBank = secondaryBank(area);
        fillData.setVisibility(item.getData().isEmpty() || secondaryBank < 0
                ? View.GONE : View.VISIBLE);
        if (secondaryBank >= 0) {
            fillData.setText(fillDataLabel(area));
            fillData.setOnClickListener(view -> {
                listener.onFillMask(secondaryBank, item.getData());
                dialog.dismiss();
            });
        }
        content.findViewById(R.id.btn_inventory_detail_close)
                .setOnClickListener(view -> dialog.dismiss());
    }

    public void show() {
        dialog.show();
    }

    public void dismiss() {
        dialog.dismiss();
    }

    public void setOnDismissListener(DialogInterface.OnDismissListener listener) {
        dialog.setOnDismissListener(listener);
    }

    private static void setText(View root, int id, CharSequence value) {
        ((TextView) root.findViewById(id)).setText(value);
    }

    private static String idLabel(InventoryArea area) {
        return area.getProtocol() == TagProtocol.ISO_18000_6C ? "EPC" : "标签";
    }

    private static String dataLabel(InventoryArea area) {
        return switch (area) {
            case C_EPC_TID -> "TID";
            case C_EPC_USER, B_UID_USER, GJB_CODE_USER, GB_CODE_USER -> "USER";
            case C_EPC_RESERVED -> "RSRV";
            default -> "DATA";
        };
    }

    private static int idBank(InventoryArea area) {
        return area.getProtocol() == TagProtocol.ISO_18000_6C
                || area.getProtocol() == TagProtocol.GB_T_29768 ? 1 : 0;
    }

    private static int secondaryBank(InventoryArea area) {
        return switch (area) {
            case C_EPC_RESERVED -> 0;
            case C_EPC_TID -> 2;
            case C_EPC_USER, GB_CODE_USER -> 3;
            case GB_CODE_INFO -> 0;
            default -> -1;
        };
    }

    private static int fillDataLabel(InventoryArea area) {
        return switch (area) {
            case C_EPC_TID -> R.string.inventory_detail_fill_tid;
            case C_EPC_RESERVED -> R.string.inventory_detail_fill_reserved;
            default -> R.string.inventory_detail_fill_user;
        };
    }
}
