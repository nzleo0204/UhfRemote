package com.leo.remote.ui.reader.inventory;

import static org.junit.Assert.assertEquals;

import com.leo.remote.reader.model.InventoryItem;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Test;

public final class InventoryCsvExporterTest {
    @Test
    public void writesStableUtf8CsvAndEscapesQuotes() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        InventoryCsvExporter.write(output, List.of(
                new InventoryItem("EPC\"1", "AABB", -45, 3, "Chip,One")));

        assertEquals("index,id,additional_data,count,rssi,chip_model\r\n"
                        + "1,\"EPC\"\"1\",\"AABB\",3,-45,\"Chip,One\"\r\n",
                output.toString(StandardCharsets.UTF_8));
    }
}
