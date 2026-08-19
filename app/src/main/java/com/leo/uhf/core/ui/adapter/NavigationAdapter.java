package com.leo.uhf.core.ui.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.leo.uhf.R;
import com.leo.uhf.core.ui.base.BaseAdapter;
import com.leo.uhf.core.ui.adapter.NavigationAdapter.NavigationItem;

/** 导航栏适配器 */
public final class NavigationAdapter extends BaseAdapter<NavigationItem>
        implements com.hjq.base.BaseAdapter.OnItemClickListener {

    /** 当前选中条目位置 */
    private int selectedPosition = 0;

    /** 导航栏点击监听 */
    @Nullable
    private OnNavigationListener listener;

    public NavigationAdapter(@NonNull Context context) {
        super(context);
        setOnItemClickListener(this);
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder();
    }

    @NonNull
    @Override
    protected RecyclerView.LayoutManager generateDefaultLayoutManager(@NonNull Context context) {
        int spanCount = context.getResources().getBoolean(R.bool.home_navigation_rail) ? 1 : getCount();
        return new GridLayoutManager(context, spanCount, RecyclerView.VERTICAL, false);
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    public void setSelectedPosition(int position) {
        if (selectedPosition == position) {
            return;
        }
        int oldPosition = selectedPosition;
        selectedPosition = position;
        notifyItemChanged(oldPosition);
        notifyItemChanged(position);
    }

    /**
     * 设置导航栏监听
     */
    public void setOnNavigationListener(@Nullable OnNavigationListener listener) {
        this.listener = listener;
    }

    /**
     * {@link BaseAdapter.OnItemClickListener}
     */

    @Override
    public void onItemClick(@NonNull RecyclerView recyclerView, @NonNull View itemView, int position) {
        if (selectedPosition == position) {
            return;
        }

        if (listener == null || listener.onNavigationItemSelected(position)) {
            int oldPosition = selectedPosition;
            selectedPosition = position;
            notifyItemChanged(oldPosition);
            notifyItemChanged(position);
        }
    }

    private final class ViewHolder extends AppViewHolder {

        private final ImageView iconView;
        private final TextView titleView;

        private ViewHolder() {
            super(R.layout.home_navigation_item);
            iconView = findViewById(R.id.iv_home_navigation_icon);
            titleView = findViewById(R.id.tv_home_navigation_title);
        }

        @Override
        public void onBindView(int position) {
            NavigationItem item = getItem(position);
            boolean selected = selectedPosition == position;
            Drawable icon = item.drawable == null ? null : item.drawable.mutate();
            iconView.setImageDrawable(icon);
            titleView.setText(item.text);
            itemView.setSelected(selected);
            iconView.setSelected(selected);
            titleView.setSelected(selected);
            int tintColor = ContextCompat.getColor(getContext(),
                    selected ? R.color.white : R.color.rfid_text_muted);
            ImageViewCompat.setImageTintList(iconView, ColorStateList.valueOf(tintColor));
        }
    }

    public static class NavigationItem {

        public final String text;
        public final Drawable drawable;

        public NavigationItem(String text, Drawable drawable) {
            this.text = text;
            this.drawable = drawable;
        }
    }

    public interface OnNavigationListener {

        boolean onNavigationItemSelected(int position);
    }
}
