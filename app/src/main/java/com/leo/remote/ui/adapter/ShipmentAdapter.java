package com.leo.remote.ui.adapter;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.hjq.toast.Toaster;
import com.leo.remote.R;
import com.leo.remote.data.model.Shipment;
import com.leo.remote.data.model.ShipmentStatus;
import com.leo.remote.data.model.TimelineNode;
import com.leo.remote.util.RfidFormat;
import java.util.List;
import java.util.Objects;

public final class ShipmentAdapter extends ListAdapter<Shipment, ShipmentAdapter.ViewHolder> {
    private static final DiffUtil.ItemCallback<Shipment> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull Shipment oldItem, @NonNull Shipment newItem) {
            return Objects.equals(oldItem.orderNo, newItem.orderNo)
                    && Objects.equals(oldItem.batchNo, newItem.batchNo);
        }

        @Override
        public boolean areContentsTheSame(@NonNull Shipment oldItem, @NonNull Shipment newItem) {
            return oldItem.quantity == newItem.quantity
                    && oldItem.shipTime == newItem.shipTime
                    && Objects.equals(oldItem.status, newItem.status)
                    && Objects.equals(oldItem.orderNo, newItem.orderNo)
                    && Objects.equals(oldItem.batchNo, newItem.batchNo)
                    && Objects.equals(oldItem.trackingNo, newItem.trackingNo)
                    && Objects.equals(oldItem.carrier, newItem.carrier)
                    && sameTimeline(oldItem.timeline, newItem.timeline);
        }
    };

    public ShipmentAdapter() {
        super(DIFF);
    }

    public void submit(List<Shipment> data) {
        submitList(List.copyOf(data));
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.shipment_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Shipment item = getItem(position);
        holder.orderNo.setText(item.orderNo);
        holder.status.setText(item.status.label);
        holder.status.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), statusColor(item.status)));
        Context context = holder.itemView.getContext();
        holder.meta.setText(context.getString(R.string.shipment_meta, item.batchNo,
                RfidFormat.quantity(item.quantity), item.carrier, RfidFormat.time(item.shipTime)));
        holder.tracking.setText(context.getString(R.string.shipment_tracking_no, item.trackingNo));
        holder.tracking.setOnClickListener(v -> copy(v.getContext(), item.trackingNo));
        holder.timeline.removeAllViews();
        for (TimelineNode node : item.timeline) {
            TextView line = new TextView(holder.itemView.getContext());
            line.setText(context.getString(node.done ? R.string.shipment_timeline_done
                            : R.string.shipment_timeline_pending,
                    node.title, node.desc, RfidFormat.time(node.time)));
            line.setTextColor(ContextCompat.getColor(holder.itemView.getContext(),
                    node.done ? R.color.rfid_text_secondary : R.color.rfid_text_muted));
            line.setTextSize(12);
            line.setPadding(0, 8, 0, 8);
            holder.timeline.addView(line, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private static boolean sameTimeline(List<TimelineNode> oldItems, List<TimelineNode> newItems) {
        if (oldItems == newItems) {
            return true;
        }
        if (oldItems == null || newItems == null || oldItems.size() != newItems.size()) {
            return false;
        }
        for (int index = 0; index < oldItems.size(); index++) {
            TimelineNode oldItem = oldItems.get(index);
            TimelineNode newItem = newItems.get(index);
            if (oldItem.time != newItem.time || oldItem.done != newItem.done
                    || !Objects.equals(oldItem.title, newItem.title)
                    || !Objects.equals(oldItem.desc, newItem.desc)) {
                return false;
            }
        }
        return true;
    }

    private static int statusColor(ShipmentStatus status) {
        return switch (status) {
            case DELIVERED -> R.color.rfid_success;
            case IN_TRANSIT, SHIPPED -> R.color.rfid_warning;
            case PREPARING -> R.color.rfid_text_muted;
        };
    }

    private static void copy(Context context, String value) {
        ClipboardManager manager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        manager.setPrimaryClip(ClipData.newPlainText("tracking_no", value));
        Toaster.show("运单号已复制");
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final TextView orderNo;
        final TextView status;
        final TextView meta;
        final TextView tracking;
        final LinearLayout timeline;

        ViewHolder(android.view.View view) {
            super(view);
            orderNo = view.findViewById(R.id.tv_shipment_order_no);
            status = view.findViewById(R.id.tv_shipment_status);
            meta = view.findViewById(R.id.tv_shipment_meta);
            tracking = view.findViewById(R.id.tv_shipment_tracking);
            timeline = view.findViewById(R.id.ll_shipment_timeline);
        }
    }
}
