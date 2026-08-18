package com.leo.remote.business.stock.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.leo.remote.R;
import com.leo.remote.business.stock.data.model.StockItem;
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
                    && oldItem.unitPrice == newItem.unitPrice
                    && Objects.equals(oldItem.productName, newItem.productName)
                    && Objects.equals(oldItem.chipModel, newItem.chipModel)
                    && Objects.equals(oldItem.warehouse, newItem.warehouse)
                    && Objects.equals(oldItem.spec, newItem.spec)
                    && Objects.equals(oldItem.imageUrl, newItem.imageUrl)
                    && Objects.equals(oldItem.tags, newItem.tags);
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
        holder.image.setImageResource(imageResource(item.imageUrl));
        android.content.Context context = holder.itemView.getContext();
        holder.meta.setText(context.getString(R.string.stock_meta, item.chipModel, item.spec));
        holder.qty.setText(context.getString(R.string.stock_available, RfidFormat.quantity(item.availableQty)));
        holder.price.setText(context.getString(R.string.stock_price, item.unitPrice));
        bindTags(holder, item.tags);
    }

    private static void bindTags(ViewHolder holder, List<String> tags) {
        TextView[] views = {holder.tagOne, holder.tagTwo};
        for (int index = 0; index < views.length; index++) {
            TextView tagView = views[index];
            if (tags != null && index < tags.size()) {
                String tag = tags.get(index);
                tagView.setVisibility(View.VISIBLE);
                tagView.setText(tag);
                boolean green = "定制".equals(tag);
                boolean blue = "背胶".equals(tag);
                tagView.setBackgroundResource(green ? R.drawable.rfid_chip_green_bg
                        : blue ? R.drawable.rfid_chip_blue_bg : R.drawable.rfid_chip_gray_bg);
                tagView.setTextColor(ContextCompat.getColor(holder.itemView.getContext(),
                        green || blue ? R.color.white : R.color.rfid_text_secondary));
            } else {
                tagView.setVisibility(View.GONE);
            }
        }
    }

    private static int imageResource(String image) {
        return switch (image) {
            case "product_alien" -> R.drawable.rfid_product_alien;
            case "product_ucode" -> R.drawable.rfid_product_ucode;
            case "product_e710" -> R.drawable.rfid_product_e710;
            default -> R.drawable.rfid_product_monza;
        };
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final ImageView image;
        final TextView meta;
        final TextView qty;
        final TextView price;
        final LinearLayout tags;
        final TextView tagOne;
        final TextView tagTwo;

        ViewHolder(View view) {
            super(view);
            name = view.findViewById(R.id.tv_stock_item_name);
            image = view.findViewById(R.id.iv_stock_item_image);
            meta = view.findViewById(R.id.tv_stock_item_meta);
            qty = view.findViewById(R.id.tv_stock_item_qty);
            price = view.findViewById(R.id.tv_stock_item_price);
            tags = view.findViewById(R.id.ll_stock_item_tags);
            tagOne = view.findViewById(R.id.tv_stock_item_tag_one);
            tagTwo = view.findViewById(R.id.tv_stock_item_tag_two);
        }
    }
}
