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
import androidx.recyclerview.widget.RecyclerView;
import com.hjq.toast.Toaster;
import com.leo.remote.R;
import com.leo.remote.data.model.Shipment;
import com.leo.remote.data.model.ShipmentStatus;
import com.leo.remote.data.model.TimelineNode;
import com.leo.remote.util.RfidFormat;
import java.util.ArrayList;
import java.util.List;

public final class ShipmentAdapter extends RecyclerView.Adapter<ShipmentAdapter.ViewHolder> {
    private final List<Shipment> items = new ArrayList<>();

    public void submit(List<Shipment> data) {
        int oldSize = items.size();
        items.clear();
        notifyItemRangeRemoved(0, oldSize);
        items.addAll(data);
        notifyItemRangeInserted(0, data.size());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.shipment_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Shipment item = items.get(position);
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

    @Override
    public int getItemCount() {
        return items.size();
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
