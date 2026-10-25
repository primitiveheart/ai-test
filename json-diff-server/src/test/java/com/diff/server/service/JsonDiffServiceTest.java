package com.diff.server.service;

import com.diff.server.config.DiffConfigFactory;
import com.diff.server.config.DiffProperties;
import com.diff.server.exception.InvalidRequestException;
import com.diff.server.exception.PayloadTooLargeException;
import com.diff.server.model.DiffRequest;
import com.diff.server.model.DiffResponse;
import com.diff.server.model.DiffRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonDiffServiceTest {

    private final DiffProperties properties = new DiffProperties();
    private final JsonDiffService service =
            new JsonDiffService(new DiffConfigFactory(properties), properties, new ObjectMapper());

    @Test
    void returnsOkEnvelopeWithSummary() {
        DiffRequest request = request(
                "{\"name\":\"Tom\",\"age\":18}",
                "{\"name\":\"Jerry\",\"age\":18,\"email\":\"j@example.com\"}");

        DiffResponse response = service.compare(request);

        assertTrue(response.isOk());
        assertEquals("ok", response.getStatus());
        assertEquals(1, response.getSummary().getAdded());
        assertEquals(0, response.getSummary().getDeleted());
        assertEquals(1, response.getSummary().getModified());
        assertEquals(2, response.getSummary().getTotal());
    }

    @Test
    void rowsUseFrontendFieldNames() {
        DiffResponse response = service.compare(request("{\"a\":1}", "{\"a\":2}"));

        assertEquals(1, response.getDiffResult().size());
        DiffRow row = response.getDiffResult().get(0);
        assertEquals("a", row.getKey());
        assertEquals("1", row.getOldValue());
        assertEquals("2", row.getNewValue());
    }

    @Test
    void missingSideSerializesAsNull() {
        DiffResponse response = service.compare(request("{\"a\":1}", "{\"a\":1,\"b\":2}"));

        assertEquals(1, response.getDiffResult().size());
        DiffRow row = response.getDiffResult().get(0);
        assertEquals("b", row.getKey());
        assertNull(row.getOldValue());
        assertEquals("2", row.getNewValue());
    }

    @Test
    void honoursIgnorePathsFromRequest() {
        DiffRequest request = request(
                "{\"meta\":{\"id\":1},\"name\":\"Tom\"}",
                "{\"meta\":{\"id\":2},\"name\":\"Jerry\"}");
        request.setIgnorePaths(Collections.singletonList("meta.**"));

        DiffResponse response = service.compare(request);

        assertEquals(1, response.getDiffResult().size());
        assertEquals("name", response.getDiffResult().get(0).getKey());
    }

    @Test
    void honoursMaxDepthFromRequest() {
        DiffRequest request = request("{\"a\":{\"b\":1}}", "{\"a\":{\"b\":2}}");
        request.setMaxDepth(1);

        DiffResponse response = service.compare(request);

        // 深度达到上限，整棵子树作为一个值比较
        assertEquals(1, response.getDiffResult().size());
        assertEquals("a", response.getDiffResult().get(0).getKey());
        assertEquals("{\"b\":1}", response.getDiffResult().get(0).getOldValue());
        assertEquals("{\"b\":2}", response.getDiffResult().get(0).getNewValue());
    }

    @Test
    void honoursStrictNumbersFromRequest() {
        DiffRequest request = request("{\"a\":1}", "{\"a\":1.0}");
        request.setLenientNumbers(false);

        assertEquals(1, service.compare(request).getDiffResult().size());
    }

    @Test
    void identicalJsonYieldsEmptyDiffResult() {
        DiffResponse response = service.compare(request("{\"a\":1}", "{\"a\":1}"));

        assertTrue(response.isOk());
        assertTrue(response.getDiffResult().isEmpty());
        assertEquals(0, response.getSummary().getTotal());
    }

    @Test
    void rejectsMalformedJson() {
        DiffRequest request = request("{not-json", "{}");

        assertThrows(InvalidRequestException.class, () -> service.compare(request));
    }

    @Test
    void rejectsOversizedPayload() {
        DiffProperties small = new DiffProperties();
        small.setMaxJsonLength(10);
        JsonDiffService limitedService =
                new JsonDiffService(new DiffConfigFactory(small), small, new ObjectMapper());

        DiffRequest request = request("{\"a\":1,\"b\":2,\"c\":3}", "{}");

        assertThrows(PayloadTooLargeException.class, () -> limitedService.compare(request));
    }

    @Test
    void treatsBlankInputAsEmptyDocument() {
        DiffRequest request = request("", "{}");

        DiffResponse response = service.compare(request);

        assertTrue(response.isOk(), "一侧空白应当视为空文档，不应报错");
    }

    @Test
    void rejectsBothSidesBlank() {
        DiffRequest request = request("   ", "");

        InvalidRequestException e =
                assertThrows(InvalidRequestException.class, () -> service.compare(request));

        assertTrue(e.getMessage().contains("oldResult"), e.getMessage());
        assertTrue(e.getMessage().contains("newResult"), e.getMessage());
    }

    // ------------------------------------------------------------ 辅助方法

    private static DiffRequest request(String oldResult, String newResult) {
        DiffRequest request = new DiffRequest();
        request.setOldResult(oldResult);
        request.setNewResult(newResult);
        request.setIgnorePaths(Collections.<String>emptyList());
        return request;
    }

    private static List<String> keysOf(List<DiffRow> rows) {
        List<String> keys = new java.util.ArrayList<>(rows.size());
        for (DiffRow row : rows) {
            keys.add(row.getKey());
        }
        return keys;
    }

    @Test
    void rowsKeepDeclarationOrder() {
        DiffResponse response = service.compare(
                request("{\"a\":1,\"b\":2,\"c\":3}", "{\"a\":9,\"b\":8,\"c\":7}"));

        assertEquals(Arrays.asList("a", "b", "c"), keysOf(response.getDiffResult()));
    }
}
