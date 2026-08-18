package com.leo.remote.rfid.demo.ui.inventory;

import com.leo.remote.rfid.sdk.model.InventoryItem;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

public final class InventoryCsvExporter {
    private InventoryCsvExporter() {}

    public static void write(OutputStream output, List<InventoryItem> items) throws IOException {
        try (OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
             BufferedWriter buffered = new BufferedWriter(writer)) {
            buffered.write("index,id,additional_data,count,rssi,chip_model\r\n");
            for (int i = 0; i < items.size(); i++) {
                InventoryItem item = items.get(i);
                buffered.write(String.format(Locale.ROOT, "%d,%s,%s,%d,%d,%s\r\n",
                        i + 1, escape(item.getId()), escape(item.getData()), item.getCount(),
                        item.getRssi(), escape(item.getChipModel())));
            }
        }
    }

    private static String escape(String value) {
        String safe = value == null ? "" : value;
        return '"' + safe.replace("\"", "\"\"") + '"';
    }
}
