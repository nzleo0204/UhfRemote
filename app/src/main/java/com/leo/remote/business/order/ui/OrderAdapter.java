package com.leo.remote.business.order.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.ImageView;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.leo.remote.R;
import com.leo.remote.business.order.data.model.Order;
import com.leo.remote.business.order.data.model.OrderStatus;
import com.leo.remote.core.util.RfidFormat;
import java.util.List;
import java.util.Objects;

public final class OrderAdapter extends ListAdapter<Order, OrderAdapter.ViewHolder> {
    private static final DiffUtil.ItemCallback<Order> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull Order oldItem, @NonNull Order newItem) {
            return Objects.equals(oldItem.orderNo, newItem.orderNo);
        }

        @Override
        public boolean areContentsTheSame(@NonNull Order oldItem, @NonNull Order newItem) {
            return oldItem.quantity == newItem.quantity
                    && oldItem.progress == newItem.progress
                    && oldItem.shippedQty == newItem.shippedQty
                    && oldItem.pendingQty == newItem.pendingQty
                    && oldItem.submitTime == newItem.submitTime
                    && oldItem.finishTime == newItem.finishTime
                    && Objects.equals(oldItem.status, newItem.status)
                    && Objects.equals(oldItem.orderNo, newItem.orderNo)
                    && Objects.equals(oldItem.productName, newItem.productName)
                    && Objects.equals(oldItem.customRequirement, newItem.customRequirement)
                    && Objects.equals(oldItem.processImages, newItem.processImages)
                    && Objects.equals(oldItem.imageUrl, newItem.imageUrl);
        }
    };

    public OrderAdapter() {
        super(DIFF);
    }

    public void submit(List<Order> data) {
        submitList(List.copyOf(data));
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.order_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Order item = getItem(position);
        holder.no.setText(item.orderNo);
        holder.status.setText(item.status.label);
        holder.status.setBackgroundResource(statusBackground(item.status));
        holder.status.setTextColor(ContextCompat.getColor(holder.itemView.getContext(),
                statusTextColor(item.status)));
        holder.image.setImageResource(imageResource(item.imageUrl));
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
        holder.progressBlock.setVisibility(production ? View.VISIBLE : View.GONE);
        holder.progressLabel.setVisibility(production ? View.VISIBLE : View.GONE);
        holder.progress.setVisibility(production ? View.VISIBLE : View.GONE);
        holder.progressValue.setVisibility(production ? View.VISIBLE : View.GONE);
        holder.processLabel.setVisibility(production ? View.VISIBLE : View.GONE);
        holder.processScroll.setVisibility(production ? View.VISIBLE : View.GONE);
        holder.progress.setProgress(item.progress);
        holder.progressValue.setText(context.getString(R.string.order_progress_value, item.progress));
        holder.processImages.removeAllViews();
        if (production && item.processImages != null) {
            for (String image : item.processImages) {
                ImageView imageView = new ImageView(context);
                imageView.setBackgroundResource(R.drawable.rfid_thumb_bg);
                imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                imageView.setImageResource(imageResource(image));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        context.getResources().getDimensionPixelSize(R.dimen.dp_58),
                        context.getResources().getDimensionPixelSize(R.dimen.dp_40));
                params.setMarginEnd(context.getResources().getDimensionPixelSize(R.dimen.dp_8));
                holder.processImages.addView(imageView, params);
            }
        }
    }

    private static int statusBackground(OrderStatus status) {
        return switch (status) {
            case IN_PRODUCTION -> R.drawable.rfid_chip_blue_bg;
            case PARTIAL_SHIPPED -> R.drawable.rfid_chip_warning_outline_bg;
            case PENDING -> R.drawable.rfid_chip_gray_bg;
            case COMPLETED -> R.drawable.rfid_chip_green_bg;
        };
    }

    private static int statusTextColor(OrderStatus status) {
        return switch (status) {
            case IN_PRODUCTION, COMPLETED -> R.color.white;
            case PARTIAL_SHIPPED -> R.color.rfid_warning;
            case PENDING -> R.color.rfid_text_secondary;
        };
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
        final TextView no;
        final TextView status;
        final ImageView image;
        final TextView product;
        final TextView qty;
        final TextView detail;
        final TextView progressLabel;
        final TextView progressValue;
        final ProgressBar progress;
        final LinearLayout progressBlock;
        final TextView processLabel;
        final HorizontalScrollView processScroll;
        final LinearLayout processImages;

        ViewHolder(View view) {
            super(view);
            no = view.findViewById(R.id.tv_order_no);
            status = view.findViewById(R.id.tv_order_status);
            image = view.findViewById(R.id.iv_order_product_image);
            product = view.findViewById(R.id.tv_order_product);
            qty = view.findViewById(R.id.tv_order_qty);
            detail = view.findViewById(R.id.tv_order_detail);
            progressLabel = view.findViewById(R.id.tv_order_progress_label);
            progressValue = view.findViewById(R.id.tv_order_progress_value);
            progress = view.findViewById(R.id.pb_order_progress);
            progressBlock = view.findViewById(R.id.ll_order_progress);
            processLabel = view.findViewById(R.id.tv_order_process_label);
            processScroll = view.findViewById(R.id.hsv_order_process);
            processImages = view.findViewById(R.id.ll_order_process_images);
        }
    }
}
