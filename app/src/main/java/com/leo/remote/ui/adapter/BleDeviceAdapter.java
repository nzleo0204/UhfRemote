package com.leo.remote.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import cn.wandersnail.ble.Device;
import com.leo.remote.R;

public final class BleDeviceAdapter extends ListAdapter<BleDeviceAdapter.Item, BleDeviceAdapter.ViewHolder> {
    public interface Listener { void onDeviceSelected(Device device); }

    public static final class Item {
        public final Device device;
        public final String name;
        public final String address;
        public final int rssi;

        public Item(Device device, String name, String address, int rssi) {
            this.device = device;
            this.name = name;
            this.address = address;
            this.rssi = rssi;
        }
    }

    private static final DiffUtil.ItemCallback<Item> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull Item oldItem, @NonNull Item newItem) {
            return oldItem.address.equalsIgnoreCase(newItem.address);
        }

        @Override
        public boolean areContentsTheSame(@NonNull Item oldItem, @NonNull Item newItem) {
            return oldItem.name.equals(newItem.name) && oldItem.address.equals(newItem.address)
                    && oldItem.rssi == newItem.rssi;
        }
    };

    private final Listener listener;

    public BleDeviceAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).address.toUpperCase(java.util.Locale.US).hashCode();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.ble_device_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Item item = getItem(position);
        holder.name.setText(item.name);
        holder.address.setText(item.address);
        holder.rssi.setText(item.rssi + " dBm");
        holder.itemView.setOnClickListener(view -> listener.onDeviceSelected(item.device));
    }

    static final class ViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
        final TextView name;
        final TextView address;
        final TextView rssi;

        ViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tv_ble_device_name);
            address = itemView.findViewById(R.id.tv_ble_device_address);
            rssi = itemView.findViewById(R.id.tv_ble_device_rssi);
        }
    }
}
