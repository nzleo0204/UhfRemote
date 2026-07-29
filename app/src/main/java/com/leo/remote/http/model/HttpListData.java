package com.leo.remote.http.model;

import androidx.annotation.Nullable;
import java.util.List;

public class HttpListData<T> extends HttpData<HttpListData.ListBean<T>> {
    public static class ListBean<T> {
        private int pageIndex;
        private int pageSize;
        private int totalNumber;
        @Nullable
        private List<T> items;

        public boolean isLastPage() {
            if (items == null || pageSize == 0) {
                return true;
            }
            return Math.ceil((float) totalNumber / pageSize) <= pageIndex;
        }

        public int getTotalNumber() {
            return totalNumber;
        }

        public int getPageIndex() {
            return pageIndex;
        }

        public int getPageSize() {
            return pageSize;
        }

        @Nullable
        public List<T> getItems() {
            return items;
        }
    }
}
