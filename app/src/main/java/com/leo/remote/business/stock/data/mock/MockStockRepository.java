package com.leo.remote.business.stock.data.mock;

import android.text.TextUtils;
import com.leo.remote.data.DataCallback;
import com.leo.remote.business.stock.data.model.StockItem;
import com.leo.remote.business.stock.data.StockRepository;
import com.leo.remote.core.data.mock.BaseMockRepository;
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
                new StockItem("H47 Monza R6", "860-960MHz", 1280, 0,
                        "深圳仓 A-03", "EPC Gen2", "product_monza", now - 3600_000L,
                        0.38, List.of("背胶", "定制")),
                new StockItem("Alien H3 标准型", "902-928MHz", 3540, 0,
                        "深圳仓 B-11", "ISO 18000-6C", "product_alien", now - 7200_000L,
                        0.22, List.of("无芯片", "标准")),
                new StockItem("NXP UCODE 9", "840-960MHz", 892, 0,
                        "上海仓 C-02", "EPC Gen2", "product_ucode", now - 10_800_000L,
                        0.65, List.of("背胶", "标准")),
                new StockItem("Impinj E710 定制型", "865-868MHz", 156, 0,
                        "上海仓 C-02", "EPC Gen2", "product_e710", now - 14_400_000L,
                        1.20, List.of("定制")),
                new StockItem("UHF 抗金属标签", "860-960MHz", 438, 0,
                        "北京仓 D-08", "ISO 18000-6C", "product_ucode", now - 18_000_000L,
                        1.85, List.of("抗金属", "标准")),
                new StockItem("柔性可打印标签", "902-928MHz", 2160, 0,
                        "深圳仓 A-12", "EPC Gen2", "product_alien", now - 21_600_000L,
                        0.31, List.of("背胶", "标准")));
    }
}
