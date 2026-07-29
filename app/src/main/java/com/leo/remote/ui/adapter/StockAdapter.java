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
import com.leo.remote.data.model.StockItem;
import com.leo.remote.util.RfidFormat;
import java.util.List;
import java.util.Objects;

public final class StockAdapter extends ListAdapter<StockItem, StockAdapter.ViewHolder> {
    private static final DiffUtil.ItemCallback<StockItem> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull StockItem oldItem, @NonNull StockItem newItem) {
            return Objects.equals(oldItem.productName, newItem.productName)
                    && Objects.equals(oldItem.chipModel, newItem.chipModel)
                    && Objects.equals(oldItem.warehouse, newItem.warehouse);
        }

        @Override
        public boolean areContentsTheSame(@NonNull StockItem oldItem, @NonNull StockItem newItem) {
            return oldItem.availableQty == newItem.availableQty
                    && oldItem.reservedQty == newItem.reservedQty
                    && oldItem.updateTime == newItem.updateTime
                    && Objects.equals(oldItem.productName, newItem.productName)
                    && Objects.equals(oldItem.chipModel, newItem.chipModel)
                    && Objects.equals(oldItem.warehouse, newItem.warehouse)
                    && Objects.equals(oldItem.spec, newItem.spec)
                    && Objects.equals(oldItem.imageUrl, newItem.imageUrl);
        }
    };

    public StockAdapter() {
        super(DIFF);
    }

    public void submit(List<StockItem> data) {
        submitList(List.copyOf(data));
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.stock_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StockItem item = getItem(position);
        holder.name.setText(item.productName);
        android.content.Context context = holder.itemView.getContext();
        holder.meta.setText(context.getString(R.string.stock_meta, item.chipModel, item.spec));
        holder.qty.setText(context.getString(R.string.stock_available, RfidFormat.quantity(item.availableQty)));
        holder.price.setText(context.getString(R.string.stock_reserved, RfidFormat.quantity(item.reservedQty)));
        holder.tags.setText(item.warehouse);
        holder.time.setText(context.getString(R.string.stock_updated, RfidFormat.time(item.updateTime)));
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
