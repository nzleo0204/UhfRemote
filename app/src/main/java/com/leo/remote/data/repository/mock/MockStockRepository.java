package com.leo.remote.data.repository.mock;

import android.text.TextUtils;
import com.leo.remote.data.DataCallback;
import com.leo.remote.data.model.StockItem;
import com.leo.remote.data.repository.StockRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MockStockRepository extends BaseMockRepository implements StockRepository {
    @Override
    public void queryStock(String keyword, DataCallback<List<StockItem>> callback) {
        List<StockItem> data = data();
        if (!TextUtils.isEmpty(keyword)) {
            String query = keyword.toLowerCase(Locale.ROOT);
            List<StockItem> filtered = new ArrayList<>();
            for (StockItem item : data) {
                if (item.productName.toLowerCase(Locale.ROOT).contains(query)
                        || item.chipModel.toLowerCase(Locale.ROOT).contains(query)
                        || item.warehouse.toLowerCase(Locale.ROOT).contains(query)) {
                    filtered.add(item);
                }
            }
            data = filtered;
        }
        respond(callback, data, List.of());
    }

    private static List<StockItem> data() {
        long now = System.currentTimeMillis();
        return List.of(
                new StockItem("H47 Monza R6 定制款", "Monza R6", 128000, 12000,
                        "深圳仓 A-03", "30×15mm 白底黑字背胶", "", now - 3600_000L),
                new StockItem("H47 Alien H3 标准款", "Alien H3", 86000, 8000,
                        "深圳仓 B-11", "40×20mm 白底黑字背胶", "", now - 7200_000L),
                new StockItem("抗金属资产标签", "Impinj M730", 24000, 3000,
                        "上海仓 C-02", "70×25mm 抗金属泡棉", "", now - 10_800_000L));
    }
}
