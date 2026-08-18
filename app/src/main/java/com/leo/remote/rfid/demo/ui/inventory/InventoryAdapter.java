package com.leo.remote.rfid.demo.ui.inventory;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.leo.remote.R;
import com.leo.remote.rfid.sdk.model.InventoryArea;
import com.leo.remote.rfid.sdk.model.InventoryItem;
import com.leo.remote.rfid.sdk.model.InventoryMaskConfig;
import com.leo.remote.rfid.sdk.inventory.InventoryMaskMatcher;
import com.leo.remote.rfid.sdk.model.ModuleSubtype;
import com.leo.remote.rfid.sdk.model.TagProtocol;
import java.util.List;

/**
 * 盘点列表适配器
 *
 * 负责展示 RFID 标签盘点数据，支持：
 * - DiffUtil 增量更新
 * - Payload 局部刷新（只更新计数器）
 * - 动态显示/隐藏列（RSSI、芯片型号）
 * - Mask 过滤状态展示
 */
public final class InventoryAdapter extends ListAdapter<InventoryItem, InventoryAdapter.ViewHolder> {
    private static final String TAG = "UhfRemote/InventoryAdapter";
    private static final Object PAYLOAD_COUNTERS = new Object();
    private static final Object PAYLOAD_LAYOUT = new Object();

    private static final DiffUtil.ItemCallback<InventoryItem> DIFF =
            new DiffUtil.ItemCallback<InventoryItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull InventoryItem oldItem,
                        @NonNull InventoryItem newItem) {
                    return oldItem.getId().equals(newItem.getId())
                            && oldItem.getData().equals(newItem.getData());
                }

                @Override
                public boolean areContentsTheSame(@NonNull InventoryItem oldItem,
                        @NonNull InventoryItem newItem) {
                    return oldItem.getCount() == newItem.getCount()
                            && oldItem.getRssi() == newItem.getRssi()
                            && oldItem.getChipModel().equals(newItem.getChipModel());
                }

                @Override
                public Object getChangePayload(@NonNull InventoryItem oldItem,
                                               @NonNull InventoryItem newItem) {
                    return oldItem.getChipModel().equals(newItem.getChipModel())
                            ? PAYLOAD_COUNTERS : null;
                }
            };
    private boolean rssiVisible;
    private boolean chipVisible;
    @Nullable private InventoryMaskConfig maskConfig;
    private TagProtocol currentProtocol = TagProtocol.ISO_18000_6C;
    private int inventoryAddress;
    private InventoryArea currentArea = InventoryArea.C_EPC_ONLY;
    private final OnItemClickListener itemClickListener;

    public interface OnItemClickListener {
        void onItemClick(InventoryItem item);
    }

    public InventoryAdapter(OnItemClickListener listener) {
        super(DIFF);
        itemClickListener = listener;
    }

    public void submitList(List<InventoryItem> values) {
        Log.d(TAG, "更新盘点列表，数量: " + values.size());
        super.submitList(List.copyOf(values));
    }

    public void setModuleSubtype(ModuleSubtype subtype) {
        Log.d(TAG, "设置模块类型: " + subtype);
        setRssiVisible(subtype == ModuleSubtype.R2000 || subtype == ModuleSubtype.R2000_PLUS);
    }

    public void setRssiVisible(boolean visible) {
        if (rssiVisible == visible) { return; }
        rssiVisible = visible;
        notifyItemRangeChanged(0, getItemCount(), PAYLOAD_LAYOUT);
    }

    public void setChipVisible(boolean visible) {
        if (chipVisible == visible) { return; }
        chipVisible = visible;
        notifyItemRangeChanged(0, getItemCount(), PAYLOAD_LAYOUT);
    }

    public void setInventoryArea(InventoryArea area) {
        if (currentArea == area) { return; }
        currentArea = area;
        notifyItemRangeChanged(0, getItemCount(), PAYLOAD_LAYOUT);
    }

    public void setMaskConfig(@Nullable InventoryMaskConfig config) {
        if (maskConfig == config) { return; }
        Log.d(TAG, "更新 Mask 配置: " + (config != null ? "已启用" : "未启用"));
        maskConfig = config;
        notifyItemRangeChanged(0, getItemCount(), PAYLOAD_LAYOUT);
    }

    public void setMaskContext(TagProtocol protocol, int address) {
        if (currentProtocol == protocol && inventoryAddress == address) { return; }
        currentProtocol = protocol;
        inventoryAddress = address;
        notifyItemRangeChanged(0, getItemCount(), PAYLOAD_LAYOUT);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.inventory_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        InventoryItem item = getItem(position);
        holder.index.setText(String.format(java.util.Locale.US, "%03d", position + 1));
        holder.id.setText(item.getId());
        holder.data.setText(item.getData());
        holder.dataLabel.setText(dataLabel(currentArea));
        bindCounters(holder, item);
        bindVisibility(holder, item);
        bindColors(holder);
        int background = position % 2 == 0 ? R.color.rfid_panel_bg : R.color.rfid_page_bg;
        holder.itemView.setBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), background));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (payloads.contains(PAYLOAD_COUNTERS)) {
            bindCounters(holder, getItem(position));
        }
        if (payloads.contains(PAYLOAD_LAYOUT)) {
            InventoryItem item = getItem(position);
            holder.dataLabel.setText(dataLabel(currentArea));
            bindVisibility(holder, item);
            bindColors(holder);
        }
        if (!payloads.isEmpty()) {
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    private static void bindCounters(ViewHolder holder, InventoryItem item) {
        holder.count.setText(String.valueOf(item.getCount()));
        int rssi = item.getRssi();
        holder.rssi.setText(rssi == 0 ? "-" : rssi + " dBm");
        holder.rssi.setTextColor(ContextCompat.getColor(holder.itemView.getContext(),
                rssiColor(rssi)));
        holder.chip.setText(item.getChipModel().isEmpty() ? "-" : item.getChipModel());
    }

    private void bindVisibility(ViewHolder holder, InventoryItem item) {
        holder.dataRow.setVisibility(item.getData().isEmpty() ? View.GONE : View.VISIBLE);
        holder.rssi.setVisibility(rssiVisible ? View.VISIBLE : View.GONE);
        holder.chipRow.setVisibility(chipVisible ? View.VISIBLE : View.GONE);
        boolean maskMatches = InventoryMaskMatcher.matches(maskConfig, currentProtocol,
                currentArea, inventoryAddress, item);
        holder.maskLock.setImageResource(maskMatches
                ? R.drawable.rfid_lock_closed_ic : R.drawable.rfid_lock_open_ic);
        int description = maskMatches ? R.string.inventory_mask_lock_closed
                : maskConfig == null ? R.string.inventory_mask_lock_open
                : R.string.inventory_mask_lock_not_matched;
        holder.maskLock.setContentDescription(holder.itemView.getContext().getString(description));
    }

    private static String dataLabel(InventoryArea area) {
        return switch (area) {
            case C_EPC_TID -> "TID";
            case C_EPC_USER, B_UID_USER, GJB_CODE_USER, GB_CODE_USER -> "USER";
            case C_EPC_RESERVED -> "RSRV";
            default -> "DATA";
        };
    }

    private static int dataColor(InventoryArea area) {
        return switch (area) {
            case C_EPC_TID -> R.color.rfid_col_tid;
            case C_EPC_USER, B_UID_USER, GJB_CODE_USER, GB_CODE_USER -> R.color.rfid_col_user;
            case C_EPC_RESERVED -> R.color.rfid_col_reserved;
            default -> R.color.rfid_col_data;
        };
    }

    private void bindColors(ViewHolder holder) {
        android.content.Context context = holder.itemView.getContext();
        int epcColor = ContextCompat.getColor(context, R.color.rfid_col_epc);
        int dataColor = ContextCompat.getColor(context, dataColor(currentArea));
        holder.index.setTextColor(ContextCompat.getColor(context, R.color.rfid_col_index));
        holder.epcLabel.setTextColor(epcColor);
        holder.epcLabel.setAlpha(0.65f);
        holder.id.setTextColor(epcColor);
        holder.dataLabel.setTextColor(dataColor);
        holder.dataLabel.setAlpha(0.65f);
        holder.data.setTextColor(dataColor);
        holder.chipLabel.setTextColor(ContextCompat.getColor(context, R.color.rfid_col_index));
        holder.chip.setTextColor(ContextCompat.getColor(context, R.color.rfid_col_chip));
        holder.count.setTextColor(ContextCompat.getColor(context, R.color.rfid_col_count));
    }

    private static int rssiColor(int rssi) {
        if (rssi == 0) {
            return R.color.rfid_text_muted;
        }
        if (rssi >= -70) {
            return R.color.rfid_success;
        }
        if (rssi >= -80) {
            return R.color.rfid_warning;
        }
        return R.color.rfid_danger;
    }

    final class ViewHolder extends RecyclerView.ViewHolder {
        final TextView index;
        final TextView id;
        final TextView epcLabel;
        final TextView data;
        final TextView dataLabel;
        final TextView count;
        final TextView rssi;
        final TextView chip;
        final TextView chipLabel;
        final ImageView maskLock;
        final View dataRow;
        final View chipRow;

        ViewHolder(View view) {
            super(view);
            index = view.findViewById(R.id.tv_inventory_index);
            id = view.findViewById(R.id.tv_inventory_id);
            epcLabel = view.findViewById(R.id.tv_inventory_epc_label);
            data = view.findViewById(R.id.tv_inventory_data);
            dataLabel = view.findViewById(R.id.tv_inventory_data_label);
            count = view.findViewById(R.id.tv_inventory_count);
            rssi = view.findViewById(R.id.tv_inventory_rssi);
            chip = view.findViewById(R.id.tv_inventory_chip);
            chipLabel = view.findViewById(R.id.tv_inventory_chip_label);
            maskLock = view.findViewById(R.id.iv_inventory_mask_lock);
            dataRow = view.findViewById(R.id.row_inventory_data);
            chipRow = view.findViewById(R.id.row_inventory_chip);
            view.setOnClickListener(ignored -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && itemClickListener != null) {
                    itemClickListener.onItemClick(getItem(position));
                }
            });
        }
    }
}
