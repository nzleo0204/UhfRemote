package com.leo.remote.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.leo.remote.R;
import com.leo.remote.data.model.StockItem;
import com.leo.remote.util.RfidFormat;
import java.util.ArrayList;
import java.util.List;

public final class StockAdapter extends RecyclerView.Adapter<StockAdapter.ViewHolder> {
    private final List<StockItem> items = new ArrayList<>();

    public void submit(List<StockItem> data) {
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
                .inflate(R.layout.stock_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StockItem item = items.get(position);
        holder.name.setText(item.productName);
        android.content.Context context = holder.itemView.getContext();
        holder.meta.setText(context.getString(R.string.stock_meta, item.chipModel, item.spec));
        holder.qty.setText(context.getString(R.string.stock_available, RfidFormat.quantity(item.availableQty)));
        holder.price.setText(context.getString(R.string.stock_reserved, RfidFormat.quantity(item.reservedQty)));
        holder.tags.setText(item.warehouse);
        holder.time.setText(context.getString(R.string.stock_updated, RfidFormat.time(item.updateTime)));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView meta;
        final TextView qty;
        final TextView price;
        final TextView tags;
        final TextView time;

        ViewHolder(View view) {
            super(view);
            name = view.findViewById(R.id.tv_stock_item_name);
            meta = view.findViewById(R.id.tv_stock_item_meta);
            qty = view.findViewById(R.id.tv_stock_item_qty);
            price = view.findViewById(R.id.tv_stock_item_price);
            tags = view.findViewById(R.id.tv_stock_item_tags);
            time = view.findViewById(R.id.tv_stock_item_time);
        }
    }
}
