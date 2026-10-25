package com.diff.sdk.model;

import com.diff.sdk.JsonDiffers;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link DiffResult} 的统计、序列化与视图结构测试 */
class DiffResultTest {

    private static DiffResult diff(String oldJson, String newJson) {
        return JsonDiffers.create().diffJson(oldJson, newJson, DiffConfig.defaults());
    }

    @Test
    void emptyResultHasZeroCounts() {
        DiffResult result = DiffResult.empty();

        assertTrue(result.isEmpty());
        assertEquals(0, result.getAddedCount());
        assertEquals(0, result.getDeletedCount());
        assertEquals(0, result.getModifiedCount());
        assertEquals(0, result.size());
    }

    @Test
    void itemsListIsImmutable() {
        DiffResult result = DiffResult.empty();

        assertThrows(UnsupportedOperationException.class, () -> result.getItems().add(null));
    }

    @Test
    void summaryCountsMatchItemTypes() {
        DiffResult result = diff(
                "{\"a\":1,\"b\":2,\"c\":3,\"d\":4}",
                "{\"a\":1,\"b\":null,\"c\":30,\"e\":5}");

        // a 未变；b 删除；c 修改；d 删除；e 新增
        assertEquals(1, result.getAddedCount());
        assertEquals(2, result.getDeletedCount());
        assertEquals(1, result.getModifiedCount());
        assertEquals(4, result.size());
        assertEquals(Arrays.asList("b", "c", "d", "e"), result.getPaths());
    }

    @Test
    void filtersItemsByType() {
        DiffResult result = diff("{\"a\":1,\"b\":2,\"c\":3}", "{\"a\":1,\"b\":null,\"c\":30}");

        assertEquals(Arrays.asList("c"), pathsOf(result.getItems(DiffType.MODIFIED)));
        assertEquals(Arrays.asList("b"), pathsOf(result.getItems(DiffType.DELETED)));
        assertTrue(result.getItems(DiffType.ADDED).isEmpty());
    }

    @Test
    void toMapKeepsRawJsonValues() {
        Map<String, Object> map = diff("{\"a\":{\"b\":1}}", "{\"a\":{\"b\":2}}").toMap();

        assertEquals(1, ((Map<?, ?>) map.get("summary")).get("modified"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) map.get("items");
        assertEquals(1, items.size());
        assertEquals("a.b", items.get(0).get("path"));
        assertEquals("modified", items.get(0).get("type"));
    }

    @Test
    void toViewMapMatchesFrontendContract() {
        Map<String, Object> view = diff(
                "{\"name\":\"Tom\",\"age\":18,\"extra\":{\"x\":1},\"gone\":true}",
                "{\"name\":\"Jerry\",\"age\":18,\"extra\":{\"x\":2}}").toViewMap();

        assertEquals("ok", view.get("status"));

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) view.get("summary");
        assertEquals(0, summary.get("added"));
        assertEquals(1, summary.get("deleted"));   // gone
        assertEquals(2, summary.get("modified"));  // name, extra.x
        assertEquals(3, summary.get("total"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) view.get("diffResult");

        assertEquals(Arrays.asList("name", "extra.x", "gone"), keysOf(rows));
        assertEquals("Tom", rows.get(0).get("oldValue"));
        assertEquals("Jerry", rows.get(0).get("newValue"));
        assertEquals("1", rows.get(1).get("oldValue"));
        assertEquals("2", rows.get(1).get("newValue"));
        assertNull(rows.get(2).get("newValue"), "删除项的新值必须是 JSON null");
        assertEquals("true", rows.get(2).get("oldValue"));
    }

    @Test
    void toViewMapStringifiesContainers() {
        Map<String, Object> view = diff("{\"a\":{\"b\":1}}", "{\"a\":[1]}").toViewMap();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) view.get("diffResult");

        assertEquals(1, rows.size());
        assertEquals("{\"b\":1}", rows.get(0).get("oldValue"));
        assertEquals("[1]", rows.get(0).get("newValue"));
    }

    @Test
    void toJsonRoundTrips() {
        DiffResult result = diff("{\"a\":1}", "{\"a\":2}");

        assertTrue(result.toJson().contains("\"type\":\"modified\""));
        assertTrue(result.toViewJson().contains("\"key\":\"a\""));
    }

    @Test
    void equalityIsValueBased() {
        DiffResult a = new DiffResult(
                Arrays.asList(new DiffItem("a", DiffType.MODIFIED, 1, 2)));
        DiffResult b = new DiffResult(
                Arrays.asList(new DiffItem("a", DiffType.MODIFIED, 1, 2)));

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, DiffResult.empty());
    }

    @Test
    void toStringSummarizesCounts() {
        String text = diff("{\"a\":1,\"b\":2}", "{\"a\":1,\"b\":22}").toString();

        assertTrue(text.startsWith("DiffResult{added=0, deleted=0, modified=1"), text);
    }

    // ------------------------------------------------------------ 辅助方法

    private static List<String> pathsOf(List<DiffItem> items) {
        List<String> paths = new ArrayList<>(items.size());
        for (DiffItem item : items) {
            paths.add(item.getPath());
        }
        return paths;
    }

    private static List<String> keysOf(List<Map<String, Object>> rows) {
        List<String> keys = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            keys.add(String.valueOf(row.get("key")));
        }
        return keys;
    }
}
