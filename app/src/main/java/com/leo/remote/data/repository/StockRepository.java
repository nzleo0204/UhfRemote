package com.leo.remote.data.repository;

import com.leo.remote.data.DataCallback;
import com.leo.remote.data.model.StockItem;
import java.util.List;

public interface StockRepository {
    void queryStock(String keyword, DataCallback<List<StockItem>> callback);
}
