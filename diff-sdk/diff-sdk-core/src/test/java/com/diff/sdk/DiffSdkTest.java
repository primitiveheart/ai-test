package com.diff.sdk;

import com.diff.sdk.model.DiffConfig;
import com.diff.sdk.model.DiffItem;
import com.diff.sdk.model.DiffResult;
import com.diff.sdk.model.DiffType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiffSdkTest {

    @Test
    void diffItemExposesFields() {
        DiffItem item = new DiffItem("a.b", DiffType.MODIFIED, "x", "y");

        assertEquals("a.b", item.getPath());
        assertEquals(DiffType.MODIFIED, item.getType());
        assertEquals("x", item.getOldValue());
        assertEquals("y", item.getNewValue());
    }

    @Test
    void diffResultComputesCounts() {
        List<DiffItem> items = Arrays.asList(
                new DiffItem("a", DiffType.ADDED, null, 1),
                new DiffItem("b", DiffType.DELETED, 2, null),
                new DiffItem("c", DiffType.MODIFIED, 3, 4),
                new DiffItem("d", DiffType.MODIFIED, 5, 6)
        );
        DiffResult result = new DiffResult(items);

        assertEquals(1, result.getAddedCount());
        assertEquals(1, result.getDeletedCount());
        assertEquals(2, result.getModifiedCount());
        assertEquals(4, result.getItems().size());
    }

    @Test
    void factoryReturnsDiffer() {
        JsonDiffer differ = JsonDiffers.create();
        assertNotNull(differ);
    }

    @Test
    void facadeComparesJsonStrings() {
        DiffResult result = new DiffSdk().compare("{\"a\":1}", "{\"a\":2}");

        assertEquals(1, result.size());
        assertEquals("a", result.getItems().get(0).getPath());
        assertEquals(DiffType.MODIFIED, result.getItems().get(0).getType());
    }

    @Test
    void facadeHonoursConfiguredIgnorePaths() {
        DiffConfig config = DiffConfig.builder().ignorePaths("meta.**").build();
        DiffResult result = DiffSdk.create(config).compare(
                "{\"meta\":{\"id\":1},\"name\":\"Tom\"}",
                "{\"meta\":{\"id\":2},\"name\":\"Jerry\"}");

        assertEquals(Arrays.asList("name"), result.getPaths());
    }

    @Test
    void facadeComparesParsedTrees() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode oldNode = mapper.readTree("{\"a\":{\"b\":1}}");
        JsonNode newNode = mapper.readTree("{\"a\":{\"b\":2}}");

        DiffResult result = new DiffSdk().compare(oldNode, newNode);

        assertEquals(Arrays.asList("a.b"), result.getPaths());
    }

    @Test
    void facadeComparesPlainObjects() {
        Map<String, Object> oldValue = new LinkedHashMap<>();
        oldValue.put("name", "Tom");
        oldValue.put("age", 18);

        Map<String, Object> newValue = new LinkedHashMap<>();
        newValue.put("name", "Tom");
        newValue.put("age", 19);

        DiffResult result = new DiffSdk().compareObjects(oldValue, newValue);

        assertEquals(Arrays.asList("age"), result.getPaths());
    }

    @Test
    void blankInputsAreTreatedAsAbsent() {
        DiffSdk sdk = new DiffSdk();

        assertTrue(sdk.compare((String) null, (String) null).isEmpty());
        assertEquals(1, sdk.compare(null, "{\"a\":1}").size());
        assertNull(sdk.compare(null, "{\"a\":1}").getItems().get(0).getOldValue());
    }

    @Test
    void nullConfigFallsBackToDefaults() {
        DiffSdk sdk = new DiffSdk(null);

        assertSame(DiffConfig.defaults(), sdk.getConfig());
    }

    @Test
    void staticHelpersWorkWithoutInstantiation() {
        assertEquals(1, DiffSdk.compareJson("{\"a\":1}", "{\"a\":2}").size());

        DiffConfig config = DiffConfig.builder().ignorePaths("a").build();
        assertTrue(DiffSdk.compareJson("{\"a\":1,\"b\":1}", "{\"a\":2,\"b\":1}", config).isEmpty());
    }

    @Test
    void resultExposesFrontendViewStructure() {
        Map<String, Object> view = DiffSdk.compareJson(
                "{\"name\":\"Tom\"}", "{\"name\":\"Jerry\"}").toViewMap();

        assertEquals("ok", view.get("status"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) view.get("diffResult");
        assertEquals(1, rows.size());
        assertEquals("name", rows.get(0).get("key"));
        assertEquals("Tom", rows.get(0).get("oldValue"));
        assertEquals("Jerry", rows.get(0).get("newValue"));
    }

    @Test
    void differIsReusableAcrossCalls() {
        DiffSdk sdk = new DiffSdk();

        assertEquals(1, sdk.compare("{\"a\":1}", "{\"a\":2}").size());
        assertTrue(sdk.compare("{\"a\":1}", "{\"a\":1}").isEmpty());
        assertEquals(2, sdk.compare("{\"a\":1,\"b\":1}", "{\"a\":2,\"b\":2}").size());
    }
}
