package com.leo.remote.rfid.demo.ui.config;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import cn.wandersnail.ble.Device;
import com.leo.remote.R;
import java.util.List;

/**
 * 展示可用 BLE 读写器并转发用户选择事件。
 */
public final class BleDeviceAdapter extends ListAdapter<BleDeviceAdapter.Item, BleDeviceAdapter.ViewHolder> {
    private static final Object PAYLOAD_RSSI = new Object();

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

        @Override
        public Object getChangePayload(@NonNull Item oldItem, @NonNull Item newItem) {
            if (oldItem.name.equals(newItem.name) && oldItem.address.equals(newItem.address)) {
                return PAYLOAD_RSSI;
            }
            return null;
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
        ViewHolder holder = new ViewHolder(view);
        view.setOnClickListener(clicked -> {
            int position = holder.getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                listener.onDeviceSelected(getItem(position).device);
            }
        });
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Item item = getItem(position);
        holder.name.setText(item.name);
        holder.address.setText(item.address);
        bindRssi(holder, item);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (payloads.contains(PAYLOAD_RSSI)) {
            bindRssi(holder, getItem(position));
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    private static void bindRssi(ViewHolder holder, Item item) {
        holder.rssi.setText(holder.itemView.getContext().getString(R.string.ble_device_rssi, item.rssi));
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
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
