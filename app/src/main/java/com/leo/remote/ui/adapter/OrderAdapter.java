package com.leo.remote.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.leo.remote.R;
import com.leo.remote.data.model.Order;
import com.leo.remote.data.model.OrderStatus;
import com.leo.remote.util.RfidFormat;
import java.util.ArrayList;
import java.util.List;

public final class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.ViewHolder> {
    private final List<Order> items = new ArrayList<>();

    public void submit(List<Order> data) {
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
                .inflate(R.layout.order_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Order item = items.get(position);
        holder.no.setText(item.orderNo);
        holder.status.setText(item.status.label);
        holder.status.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), item.status.colorRes));
        holder.product.setText(item.productName);
        android.content.Context context = holder.itemView.getContext();
        holder.qty.setText(context.getString(R.string.order_quantity, RfidFormat.quantity(item.quantity)));
        if (item.status == OrderStatus.PARTIAL_SHIPPED) {
            holder.detail.setText(context.getString(R.string.order_shipment_quantity,
                    RfidFormat.quantity(item.shippedQty), RfidFormat.quantity(item.pendingQty)));
        } else if (item.status == OrderStatus.PENDING) {
            holder.detail.setText(context.getString(R.string.order_submit_time, RfidFormat.time(item.submitTime)));
        } else if (item.status == OrderStatus.COMPLETED) {
            holder.detail.setText(context.getString(R.string.order_finish_time, RfidFormat.time(item.finishTime)));
        } else {
            holder.detail.setText(context.getString(R.string.order_requirement, item.customRequirement));
        }
        boolean production = item.status == OrderStatus.IN_PRODUCTION;
        holder.progressLabel.setVisibility(production ? View.VISIBLE : View.GONE);
        holder.progress.setVisibility(production ? View.VISIBLE : View.GONE);
        holder.progress.setProgress(item.progress);
        holder.progressLabel.setText(context.getString(R.string.order_progress, item.progress));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final TextView no;
        final TextView status;
        final TextView product;
        final TextView qty;
        final TextView detail;
        final TextView progressLabel;
        final ProgressBar progress;

        ViewHolder(View view) {
            super(view);
            no = view.findViewById(R.id.tv_order_no);
            status = view.findViewById(R.id.tv_order_status);
            product = view.findViewById(R.id.tv_order_product);
            qty = view.findViewById(R.id.tv_order_qty);
            detail = view.findViewById(R.id.tv_order_detail);
            progressLabel = view.findViewById(R.id.tv_order_progress_label);
            progress = view.findViewById(R.id.pb_order_progress);
        }
    }
}
