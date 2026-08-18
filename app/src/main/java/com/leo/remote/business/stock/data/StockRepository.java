package com.leo.remote.business.stock.data;

import com.leo.remote.core.data.DataCallback;
import com.leo.remote.business.stock.data.model.StockItem;
import java.util.List;

public interface StockRepository {
    void queryStock(String keyword, DataCallback<List<StockItem>> callback);
}
