package com.leo.remote.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import androidx.annotation.IntRange;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import com.hjq.base.BaseAdapter;
import com.hjq.custom.widget.layout.WrapRecyclerView;
import java.util.ArrayList;
import java.util.List;


/**
 *    author : Android 轮子哥
 *    github : https://github.com/getActivity/AndroidProject
 *    time   : 2018/12/19
 *    desc   : RecyclerView 适配器业务基类
 */
public abstract class AppAdapter<T> extends BaseAdapter<AppAdapter<T>.AppViewHolder> {

    /** 列表数据 */
    @NonNull
    private List<T> dataSet = new ArrayList<>();

    /** 当前列表的页码，默认为第一页，用于分页加载功能 */
    private int pageNumber = 1;

    /** 是否是最后一页，默认为false，用于分页加载功能 */
    private boolean lastPage;

    /** 标记对象 */
    private Object tag;

    public AppAdapter(@NonNull Context context) {
        super(context);
    }

    @Override
    public int getItemCount() {
        return getCount();
    }

    /**
     * 获取数据总数
     */
    public int getCount() {
        return dataSet.size();
    }

    /**
     * 设置新的数据
     */
    @SuppressLint("NotifyDataSetChanged")
    public void setData(@Nullable List<T> data) {
        if (data == null) {
            dataSet.clear();
        } else {
            dataSet = data;
        }
        notifyDataSetChanged();
    }

    /**
     * 获取当前数据
     */
    @NonNull
    public List<T> getData() {
        return dataSet;
    }

    /**
     * 追加一些数据
     */
    public void addData(List<T> data) {
        if (data == null || data.isEmpty()) {
            return;
        }

        dataSet.addAll(data);
        notifyItemRangeInserted(dataSet.size() - data.size(), data.size());
    }

    /**
     * 清空当前数据
     */
    @SuppressLint("NotifyDataSetChanged")
    public void clearData() {
        dataSet.clear();
        notifyDataSetChanged();
    }

    /**
     * 是否包含了某个位置上的条目数据
     */
    public boolean containsItem(@IntRange(from = 0) int position) {
        return containsItem(getItem(position));
    }

    /**
     * 是否包含某个条目数据
     */
    public boolean containsItem(T item) {
        if (item == null) {
            return false;
        }
        return dataSet.contains(item);
    }

    /**
     * 获取某个位置上的数据
     */
    public T getItem(@IntRange(from = 0) int position) {
        return dataSet.get(position);
    }

    /**
     * 更新某个位置上的数据
     */
    public void setItem(@IntRange(from = 0) int position, @NonNull T item) {
        dataSet.set(position, item);
        notifyItemChanged(position);
    }

    /**
     * 添加单条数据
     */
    public void addItem(@NonNull T item) {
        addItem(dataSet.size(), item);
    }

    public void addItem(@IntRange(from = 0) int position, @NonNull T item) {
        if (position < dataSet.size()) {
            dataSet.add(position, item);
        } else {
            dataSet.add(item);
            position = dataSet.size() - 1;
        }
        notifyItemInserted(position);
    }

    /**
     * 删除单条数据
     */
    public void removeItem(@NonNull T item) {
        int index = dataSet.indexOf(item);
        if (index != -1) {
            removeItem(index);
        }
    }

    public void removeItem(@IntRange(from = 0) int position) {
        dataSet.remove(position);
        notifyItemRemoved(position);
    }

    /**
     * 获取当前的页码
     */
    public int getPageNumber() {
        return pageNumber;
    }

    /**
     * 设置当前的页码
     */
    public void setPageNumber(@IntRange(from = 0) int number) {
        pageNumber = number;
    }

    /**
     * 当前是否为最后一页
     */
    public boolean isLastPage() {
        return lastPage;
    }

    /**
     * 设置是否为最后一页
     */
    public void setLastPage(boolean lastPage) {
        this.lastPage = lastPage;
    }

    /**
     * 获取标记
     */
    @Nullable
    public Object getTag() {
        return tag;
    }

    /**
     * 设置标记
     */
    public void setTag(@NonNull Object tag) {
        this.tag = tag;
    }

    public abstract class AppViewHolder extends BaseAdapter<?>.BaseViewHolder {

        public AppViewHolder(@LayoutRes int id) {
            super(id);
        }

        public AppViewHolder(View itemView) {
            super(itemView);
        }

        @Override
        protected int getViewHolderPosition() {
            int position = super.getViewHolderPosition();
            RecyclerView recyclerView = getRecyclerView();
            if (recyclerView instanceof WrapRecyclerView) {
                // 这里要减去头部的数量
                position -= ((WrapRecyclerView) recyclerView).getHeaderViewsCount();
            }
            return position;
        }
    }

    public final class SimpleViewHolder extends AppViewHolder {

        public SimpleViewHolder(@LayoutRes int id) {
            super(id);
        }

        public SimpleViewHolder(View itemView) {
            super(itemView);
        }

        @Override
        public void onBindView(int position) {
            // default implementation ignored
        }
    }
}
