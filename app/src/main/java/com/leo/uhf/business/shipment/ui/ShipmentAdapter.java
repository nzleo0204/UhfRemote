package com.leo.uhf.business.shipment.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.hjq.toast.Toaster;
import com.leo.uhf.R;
import com.leo.uhf.business.shipment.data.model.Shipment;
import com.leo.uhf.business.shipment.data.model.ShipmentBatch;
import com.leo.uhf.business.shipment.data.model.ShipmentStatus;
import com.leo.uhf.business.shipment.data.model.TimelineNode;
import com.leo.uhf.core.util.RfidFormat;
import java.util.List;
import java.util.Objects;

/**
 * 将发运单记录绑定到查询列表视图。
 */
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
                    && Objects.equals(oldItem.productName, newItem.productName)
                    && sameTimeline(oldItem.timeline, newItem.timeline)
                    && sameBatches(oldItem.batches, newItem.batches);
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
        Context context = holder.itemView.getContext();
        boolean partial = item.status == ShipmentStatus.PARTIAL
                || (item.batches != null && !item.batches.isEmpty());

        holder.orderNo.setText(item.orderNo);
        holder.status.setText(item.status.label);
        holder.status.setBackgroundResource(statusBackground(item.status));
        holder.status.setTextColor(ContextCompat.getColor(context, statusTextColor(item.status)));
        holder.product.setText(item.productName);
        holder.quantity.setText(context.getString(R.string.shipment_quantity,
                RfidFormat.quantity(item.quantity)));

        holder.courier.setVisibility(partial ? View.GONE : View.VISIBLE);
        holder.batchTitle.setVisibility(partial ? View.VISIBLE : View.GONE);
        holder.batches.setVisibility(partial ? View.VISIBLE : View.GONE);
        holder.timeline.setVisibility(!partial && item.status == ShipmentStatus.DELIVERED
                ? View.VISIBLE : View.GONE);

        if (!partial) {
            holder.carrier.setText(item.carrier);
            holder.carrierStatus.setText(carrierStatus(item.status));
            holder.tracking.setText(context.getString(R.string.shipment_tracking_no,
                    item.trackingNo));
            holder.copy.setVisibility(item.trackingNo.isEmpty() ? View.GONE : View.VISIBLE);
            holder.copy.setOnClickListener(v -> copy(v.getContext(), item.trackingNo));
            bindTimeline(holder, item.timeline);
        } else {
            holder.batches.removeAllViews();
            bindBatches(holder, item.batches);
        }
    }

    private static void bindTimeline(ViewHolder holder, List<TimelineNode> nodes) {
        holder.timeline.removeAllViews();
        if (nodes == null) {
            return;
        }
        Context context = holder.itemView.getContext();
        for (TimelineNode node : nodes) {
            TextView line = new TextView(context);
            line.setText(context.getString(node.done ? R.string.shipment_timeline_done
                            : R.string.shipment_timeline_pending,
                    node.title, node.desc, RfidFormat.time(node.time)));
            line.setTextColor(ContextCompat.getColor(context,
                    node.done ? R.color.rfid_text_secondary : R.color.rfid_text_muted));
            line.setTextSize(12);
            line.setPadding(0, 8, 0, 8);
            holder.timeline.addView(line, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private static void bindBatches(ViewHolder holder, List<ShipmentBatch> batches) {
        if (batches == null) {
            return;
        }
        Context context = holder.itemView.getContext();
        int dp4 = context.getResources().getDimensionPixelSize(R.dimen.dp_4);
        int dp8 = context.getResources().getDimensionPixelSize(R.dimen.dp_8);
        int dp12 = context.getResources().getDimensionPixelSize(R.dimen.dp_12);
        int dp24 = context.getResources().getDimensionPixelSize(R.dimen.dp_24);
        for (ShipmentBatch batch : batches) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp12, dp8, dp12, dp8);
            row.setBackgroundResource(R.drawable.rfid_field_bg);

            LinearLayout summary = new LinearLayout(context);
            summary.setGravity(android.view.Gravity.CENTER_VERTICAL);
            TextView dot = new TextView(context);
            dot.setText("●");
            dot.setTextColor(ContextCompat.getColor(context,
                    batch.received ? R.color.rfid_success
                            : "运输中".equals(batch.status) ? R.color.rfid_warning
                            : R.color.rfid_text_muted));
            dot.setTextSize(12);
            summary.addView(dot, new LinearLayout.LayoutParams(dp24,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView title = new TextView(context);
            title.setText(context.getString(R.string.shipment_batch_quantity,
                    batch.title, RfidFormat.quantity(batch.quantity)));
            title.setTextColor(ContextCompat.getColor(context, R.color.rfid_text_secondary));
            title.setTextSize(12);
            summary.addView(title, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView state = new TextView(context);
            state.setText(batch.status);
            state.setTextColor(ContextCompat.getColor(context,
                    batch.received ? R.color.rfid_success
                            : "运输中".equals(batch.status) ? R.color.rfid_warning
                            : R.color.rfid_text_muted));
            state.setTextSize(11);
            summary.addView(state);
            row.addView(summary);

            TextView detail = new TextView(context);
            detail.setText(batch.detail);
            detail.setTextColor(ContextCompat.getColor(context, R.color.rfid_text_muted));
            detail.setTextSize(10);
            detail.setPadding(dp24, dp4, 0, 0);
            row.addView(detail);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 0, dp4);
            holder.batches.addView(row, params);
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

    private static boolean sameBatches(List<ShipmentBatch> oldItems, List<ShipmentBatch> newItems) {
        if (oldItems == newItems) {
            return true;
        }
        if (oldItems == null || newItems == null || oldItems.size() != newItems.size()) {
            return false;
        }
        for (int index = 0; index < oldItems.size(); index++) {
            ShipmentBatch oldItem = oldItems.get(index);
            ShipmentBatch newItem = newItems.get(index);
            if (oldItem.quantity != newItem.quantity || oldItem.received != newItem.received
                    || !Objects.equals(oldItem.title, newItem.title)
                    || !Objects.equals(oldItem.status, newItem.status)
                    || !Objects.equals(oldItem.carrier, newItem.carrier)
                    || !Objects.equals(oldItem.detail, newItem.detail)) {
                return false;
            }
        }
        return true;
    }

    private static int statusBackground(ShipmentStatus status) {
        return switch (status) {
            case SHIPPED, DELIVERED -> R.drawable.rfid_chip_green_bg;
            case PARTIAL, IN_TRANSIT -> R.drawable.rfid_chip_warning_outline_bg;
            case PREPARING -> R.drawable.rfid_chip_gray_bg;
        };
    }

    private static int statusTextColor(ShipmentStatus status) {
        return switch (status) {
            case SHIPPED, DELIVERED -> R.color.white;
            case PARTIAL, IN_TRANSIT -> R.color.rfid_warning;
            case PREPARING -> R.color.rfid_text_secondary;
        };
    }

    private static String carrierStatus(ShipmentStatus status) {
        return switch (status) {
            case SHIPPED, IN_TRANSIT -> "运输中";
            case DELIVERED -> "已签收";
            case PREPARING -> "待安排";
            case PARTIAL -> "";
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
        final TextView product;
        final TextView quantity;
        final LinearLayout courier;
        final TextView carrier;
        final TextView carrierStatus;
        final TextView tracking;
        final TextView copy;
        final TextView batchTitle;
        final LinearLayout batches;
        final LinearLayout timeline;

        ViewHolder(View view) {
            super(view);
            orderNo = view.findViewById(R.id.tv_shipment_order_no);
            status = view.findViewById(R.id.tv_shipment_status);
            product = view.findViewById(R.id.tv_shipment_product);
            quantity = view.findViewById(R.id.tv_shipment_quantity);
            courier = view.findViewById(R.id.ll_shipment_courier);
            carrier = view.findViewById(R.id.tv_shipment_carrier);
            carrierStatus = view.findViewById(R.id.tv_shipment_carrier_status);
            tracking = view.findViewById(R.id.tv_shipment_tracking);
            copy = view.findViewById(R.id.btn_shipment_copy);
            batchTitle = view.findViewById(R.id.tv_shipment_batch_title);
            batches = view.findViewById(R.id.ll_shipment_batches);
            timeline = view.findViewById(R.id.ll_shipment_timeline);
        }
    }
}
