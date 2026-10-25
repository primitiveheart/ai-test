package com.diff.sdk;

import com.diff.sdk.model.DiffConfig;
import com.diff.sdk.model.DiffItem;
import com.diff.sdk.model.DiffResult;
import com.diff.sdk.model.DiffType;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link DefaultJsonDiffer}（经 {@link JsonDiffers} 工厂创建）的对比语义测试 */
class DefaultJsonDifferTest {

    private final JsonDiffer differ = JsonDiffers.create();

    // ------------------------------------------------------------ 基础三态

    @Test
    void detectsAddedField() {
        DiffResult result = differ.diffJson("{\"name\":\"Tom\"}",
                "{\"name\":\"Tom\",\"email\":\"tom@example.com\"}");

        assertEquals(1, result.size());
        assertItem(result, "email", DiffType.ADDED, null, "tom@example.com");
        assertEquals(1, result.getAddedCount());
    }

    @Test
    void detectsDeletedField() {
        DiffResult result = differ.diffJson("{\"name\":\"Tom\",\"email\":\"tom@example.com\"}",
                "{\"name\":\"Tom\"}");

        assertEquals(1, result.size());
        assertItem(result, "email", DiffType.DELETED, "tom@example.com", null);
        assertEquals(1, result.getDeletedCount());
    }

    @Test
    void detectsModifiedField() {
        DiffResult result = differ.diffJson("{\"name\":\"Tom\",\"age\":18}",
                "{\"name\":\"Tom\",\"age\":19}");

        assertEquals(1, result.size());
        assertItem(result, "age", DiffType.MODIFIED, "18", "19");
        assertEquals(1, result.getModifiedCount());
    }

    @Test
    void identicalJsonYieldsEmptyResult() {
        DiffResult result = differ.diffJson("{\"a\":1,\"b\":[1,2],\"c\":{\"d\":true}}",
                "{\"a\":1,\"b\":[1,2],\"c\":{\"d\":true}}");

        assertTrue(result.isEmpty());
        assertEquals(0, result.size());
    }

    // ------------------------------------------------------------ 嵌套与数组

    @Test
    void usesDotPathForNestedObjects() {
        DiffResult result = differ.diffJson(
                "{\"user\":{\"name\":\"Tom\",\"address\":{\"city\":\"Beijing\"}}}",
                "{\"user\":{\"name\":\"Tom\",\"address\":{\"city\":\"Shanghai\"}}}");

        assertEquals(1, result.size());
        assertItem(result, "user.address.city", DiffType.MODIFIED, "Beijing", "Shanghai");
    }

    @Test
    void usesIndexAsPathSegmentForArrays() {
        DiffResult result = differ.diffJson(
                "{\"tags\":[\"apple\",\"banana\",\"cherry\"]}",
                "{\"tags\":[\"apple\",\"banana\",\"orange\"]}");

        assertEquals(1, result.size());
        assertItem(result, "tags.2", DiffType.MODIFIED, "cherry", "orange");
    }

    @Test
    void handlesArrayGrowAndShrink() {
        DiffResult result = differ.diffJson("{\"items\":[1,2,3]}", "{\"items\":[1,3,4,5]}");

        assertEquals(Arrays.asList("items.1", "items.2", "items.3"), result.getPaths());
        assertItem(result, "items.1", DiffType.MODIFIED, "2", "3");
        assertItem(result, "items.2", DiffType.MODIFIED, "3", "4");
        assertItem(result, "items.3", DiffType.ADDED, null, "5");
    }

    @Test
    void diffsObjectsInsideArrays() {
        DiffResult result = differ.diffJson(
                "{\"list\":[{\"id\":1,\"name\":\"a\"},{\"id\":2,\"name\":\"b\"}]}",
                "{\"list\":[{\"id\":1,\"name\":\"a\"},{\"id\":2,\"name\":\"c\"}]}");

        assertEquals(1, result.size());
        assertItem(result, "list.1.name", DiffType.MODIFIED, "b", "c");
    }

    @Test
    void detectsRootLevelAbsence() {
        assertTrue(differ.diffJson(null, "{\"a\":1}").getPaths().contains(""));
        assertEquals(DiffType.ADDED, differ.diffJson(null, "{\"a\":1}").getItems().get(0).getType());
        assertEquals(DiffType.DELETED, differ.diffJson("{\"a\":1}", "  ").getItems().get(0).getType());
        assertTrue(differ.diffJson(null, null).isEmpty());
    }

    // ------------------------------------------------------------ 类型与 null

    @Test
    void detectsTypeChanges() {
        DiffResult result = differ.diffJson(
                "{\"id\":\"1001\",\"count\":\"5\",\"active\":\"true\"}",
                "{\"id\":1001,\"count\":5,\"active\":true}");

        assertEquals(Arrays.asList("id", "count", "active"), result.getPaths());
        assertItem(result, "id", DiffType.MODIFIED, "1001", "1001");
        assertItem(result, "count", DiffType.MODIFIED, "5", "5");
        assertTrue(result.getItems().get(2).getOldValue() instanceof JsonNode);
        assertEquals("\"true\"", String.valueOf(result.getItems().get(2).getOldValue()));
        assertEquals("true", String.valueOf(result.getItems().get(2).getNewValue()));
    }

    @Test
    void treatsJsonNullAsAbsent() {
        DiffResult result = differ.diffJson(
                "{\"name\":\"Tom\",\"nickname\":null,\"remark\":\"hello\"}",
                "{\"name\":\"Tom\",\"nickname\":\"Tommy\",\"remark\":null}");

        assertEquals(Arrays.asList("nickname", "remark"), result.getPaths());
        assertItem(result, "nickname", DiffType.ADDED, null, "Tommy");
        assertItem(result, "remark", DiffType.DELETED, "hello", null);
    }

    @Test
    void containerVersusScalarIsModified() {
        DiffResult result = differ.diffJson("{\"a\":{\"b\":1}}", "{\"a\":1}");

        assertEquals(1, result.size());
        assertItem(result, "a", DiffType.MODIFIED, "{\"b\":1}", "1");
    }

    @Test
    void objectVersusArrayIsModified() {
        DiffResult result = differ.diffJson("{\"a\":{\"b\":1}}", "{\"a\":[1]}");

        assertEquals(1, result.size());
        assertEquals(DiffType.MODIFIED, result.getItems().get(0).getType());
    }

    // ------------------------------------------------------------ ignorePaths

    @Test
    void ignoresMatchingPathsByPattern() {
        DiffConfig config = DiffConfig.builder()
                .ignorePaths("meta.**", "*.timestamp")
                .build();
        DiffResult result = JsonDiffers.create(config).diffJson(
                "{\"meta\":{\"traceId\":\"a\"},\"user\":{\"timestamp\":1,\"name\":\"Tom\"}}",
                "{\"meta\":{\"traceId\":\"b\"},\"user\":{\"timestamp\":2,\"name\":\"Jerry\"}}",
                config);

        assertEquals(Arrays.asList("user.name"), result.getPaths());
        assertItem(result, "user.name", DiffType.MODIFIED, "Tom", "Jerry");
    }

    @Test
    void ignoringParentRemovesWholeSubtree() {
        DiffConfig config = DiffConfig.builder().ignorePaths("user").build();
        DiffResult result = JsonDiffers.create(config).diffJson(
                "{\"user\":{\"name\":\"Tom\",\"roles\":[\"admin\"]},\"id\":1}",
                "{\"user\":{\"name\":\"Jerry\",\"roles\":[\"user\"]},\"id\":2}",
                config);

        assertEquals(Arrays.asList("id"), result.getPaths());
        assertItem(result, "id", DiffType.MODIFIED, "1", "2");
    }

    @Test
    void sanitizedJsonExposesCleanedTrees() {
        DiffConfig config = DiffConfig.builder().ignorePaths("meta.**").build();
        JsonDiffer differWithConfig = JsonDiffers.create(config);
        DiffResult result = differWithConfig.diffJson(
                "{\"meta\":{\"traceId\":\"a\"},\"id\":1}",
                "{\"meta\":{\"traceId\":\"b\"},\"id\":2}",
                config);

        assertNotNull(result.getOldJson());
        assertNotNull(result.getNewJson());
        assertFalse(result.getOldJson().has("meta"));
        assertFalse(result.getNewJson().has("meta"));
        assertEquals(1, result.getOldJson().get("id").asInt());
        assertEquals(2, result.getNewJson().get("id").asInt());
    }

    @Test
    void sanitizedJsonIsNullWhenNothingIgnored() {
        DiffResult result = differ.diffJson("{\"a\":1}", "{\"a\":2}");

        assertNull(result.getOldJson());
        assertNull(result.getNewJson());
    }

    // ------------------------------------------------------------ maxDepth

    @Test
    void maxDepthFallsBackToWholeSubtreeComparison() {
        DiffConfig config = DiffConfig.builder().maxDepth(1).build();
        DiffResult result = JsonDiffers.create(config).diffJson(
                "{\"a\":{\"b\":{\"c\":1}}}",
                "{\"a\":{\"b\":{\"c\":2}}}",
                config);

        assertEquals(1, result.size());
        assertEquals("a", result.getPaths().get(0));
        assertEquals("{\"b\":{\"c\":1}}", String.valueOf(result.getItems().get(0).getOldValue()));
        assertEquals("{\"b\":{\"c\":2}}", String.valueOf(result.getItems().get(0).getNewValue()));
    }

    @Test
    void unlimitedDepthRecursesToLeaves() {
        DiffResult result = differ.diffJson(
                "{\"a\":{\"b\":{\"c\":1}}}",
                "{\"a\":{\"b\":{\"c\":2}}}");

        assertEquals(Arrays.asList("a.b.c"), result.getPaths());
    }

    // ------------------------------------------------------------ 数字比较

    @Test
    void lenientNumbersTreatOneAndOnePointZeroAsEqual() {
        DiffResult result = differ.diffJson("{\"a\":1,\"b\":2.50}", "{\"a\":1.0,\"b\":2.5}");

        assertTrue(result.isEmpty());
    }

    @Test
    void strictNumbersReportDifference() {
        DiffConfig config = DiffConfig.builder().lenientNumbers(false).build();
        DiffResult result = JsonDiffers.create(config).diffJson("{\"a\":1}", "{\"a\":1.0}", config);

        assertEquals(1, result.size());
        assertItem(result, "a", DiffType.MODIFIED, "1", "1.0");
    }

    @Test
    void lenientNumbersStillCatchCrossTypeChanges() {
        DiffResult result = differ.diffJson("{\"a\":1}", "{\"a\":\"1\"}");

        assertEquals(1, result.size());
        assertItem(result, "a", DiffType.MODIFIED, "1", "1");
    }

    // ------------------------------------------------------------ 入参校验

    @Test
    void rejectsMalformedJson() {
        assertThrows(IllegalArgumentException.class, () -> differ.diffJson("{not-json", "{}"));
        assertThrows(IllegalArgumentException.class, () -> differ.diffJson("{}", "{not-json"));
    }

    @Test
    void acceptsTopLevelArraysAndScalars() {
        assertEquals(1, differ.diffJson("[1,2]", "[1,3]").size());
        assertEquals(Arrays.asList("1"), differ.diffJson("[1,2]", "[1,3]").getPaths());
        assertEquals(1, differ.diffJson("1", "2").size());
        assertEquals(DiffType.MODIFIED, differ.diffJson("1", "2").getItems().get(0).getType());
    }

    @Test
    void nullConfigFallsBackToDefaults() {
        DiffResult result = JsonDiffers.create().diffJson("{\"a\":1}", "{\"a\":2}", null);

        assertEquals(1, result.size());
    }

    // ------------------------------------------------------------ 辅助方法

    private static void assertItem(DiffResult result, String path, DiffType type,
                                   String oldValue, String newValue) {
        DiffItem item = find(result, path);
        assertNotNull(item, "期望存在差异项: " + path);
        assertEquals(type, item.getType(), "path=" + path + " 的类型不符");
        assertEquals(oldValue, text(item.getOldValue()), "path=" + path + " 的 oldValue 不符");
        assertEquals(newValue, text(item.getNewValue()), "path=" + path + " 的 newValue 不符");
    }

    private static DiffItem find(DiffResult result, String path) {
        List<DiffItem> items = result.getItems();
        for (int i = 0; i < items.size(); i++) {
            DiffItem item = items.get(i);
            if (item.getPath().equals(path)) {
                return item;
            }
        }
        return null;
    }

    /** 把 JsonNode 取成字面量文本；容器用 JSON 文本，null / JSON null 统一为 null */
    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        JsonNode node = (JsonNode) value;
        if (node.isNull() || node.isMissingNode()) {
            return null;
        }
        return node.isContainerNode() ? node.toString() : node.asText();
    }
}
