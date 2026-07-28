package com.leo.remote.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.leo.remote.R;
import com.leo.remote.reader.InventoryItem;
import java.util.List;

public final class InventoryAdapter extends ListAdapter<InventoryItem, InventoryAdapter.ViewHolder> {
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
            };

    public InventoryAdapter() { super(DIFF); }

    public void submitList(List<InventoryItem> values) {
        super.submitList(List.copyOf(values));
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
        holder.data.setVisibility(item.getData().isEmpty() ? View.GONE : View.VISIBLE);
        holder.count.setText(String.valueOf(item.getCount()));
        holder.rssi.setText(String.valueOf(item.getRssi()));
        holder.chip.setText(item.getChipModel().isEmpty() ? "-" : item.getChipModel());
        holder.itemView.setBackgroundResource(position % 2 == 0 ? R.color.rfid_panel_bg : R.color.rfid_page_bg);
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final TextView index;
        final TextView id;
        final TextView data;
        final TextView count;
        final TextView rssi;
        final TextView chip;

        ViewHolder(View view) {
            super(view);
            index = view.findViewById(R.id.tv_inventory_index);
            id = view.findViewById(R.id.tv_inventory_id);
            data = view.findViewById(R.id.tv_inventory_data);
            count = view.findViewById(R.id.tv_inventory_count);
            rssi = view.findViewById(R.id.tv_inventory_rssi);
            chip = view.findViewById(R.id.tv_inventory_chip);
        }
    }
}
