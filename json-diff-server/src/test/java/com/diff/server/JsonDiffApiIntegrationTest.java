package com.diff.server;

import com.diff.server.model.DiffRequest;
import com.diff.server.model.DiffResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 端到端测试：真实启动服务，走完整链路（controller → service → diff-sdk） */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JsonDiffApiIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void compareEndpointDiffsRealJson() {
        DiffRequest request = new DiffRequest();
        request.setOldResult("{\"name\":\"Tom\",\"age\":18,\"tags\":[\"a\",\"b\"],\"remark\":\"old\"}");
        request.setNewResult("{\"name\":\"Jerry\",\"age\":18,\"tags\":[\"a\",\"c\"],\"added\":true}");
        request.setIgnorePaths(Collections.<String>emptyList());

        ResponseEntity<DiffResponse> response = post(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        DiffResponse body = response.getBody();
        assertNotNull(body);
        assertTrue(body.isOk());

        assertEquals(1, body.getSummary().getAdded());    // added
        assertEquals(1, body.getSummary().getDeleted());  // remark
        assertEquals(2, body.getSummary().getModified()); // name, tags.1
        assertEquals(4, body.getSummary().getTotal());
    }

    @Test
    void compareEndpointAppliesIgnorePaths() {
        DiffRequest request = new DiffRequest();
        request.setOldResult("{\"meta\":{\"id\":1},\"name\":\"Tom\"}");
        request.setNewResult("{\"meta\":{\"id\":2},\"name\":\"Tom\"}");
        request.setIgnorePaths(Collections.singletonList("meta.**"));

        ResponseEntity<DiffResponse> response = post(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getDiffResult().isEmpty());
    }

    @Test
    void compareEndpointReportsInvalidJson() {
        DiffRequest request = new DiffRequest();
        request.setOldResult("{oops}");
        request.setNewResult("{}");
        request.setIgnorePaths(Collections.<String>emptyList());

        ResponseEntity<DiffResponse> response = post(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("error", response.getBody().getStatus());
        assertTrue(response.getBody().getExceptionMsg().contains("oldResult"));
    }

    @Test
    void compareEndpointRejectsBothSidesBlank() {
        ResponseEntity<String> response = restTemplate.exchange("/api/json-diff/compare",
                HttpMethod.POST,
                jsonEntity("{\"oldResult\":\"   \",\"newResult\":\"\"}"),
                String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("oldResult 与 newResult 不能同时为空"));
    }

    @Test
    void healthEndpointIsReachable() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/api/json-diff/health", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("UP"));
    }

    // ------------------------------------------------------------ 辅助方法

    private ResponseEntity<DiffResponse> post(DiffRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        return restTemplate.exchange("/api/json-diff/compare", HttpMethod.POST,
                new HttpEntity<>(request, headers), DiffResponse.class);
    }

    @Test
    void missingSideIsOmittedSoFrontendSeesUndefined() {
        DiffRequest request = new DiffRequest();
        request.setOldResult("{\"gone\":1}");
        request.setNewResult("{}");
        request.setIgnorePaths(Collections.<String>emptyList());

        ResponseEntity<String> raw = restTemplate.exchange("/api/json-diff/compare",
                HttpMethod.POST, jsonEntity("{\"oldResult\":\"{\\\"gone\\\":1}\",\"newResult\":\"{}\"}"),
                String.class);

        assertEquals(HttpStatus.OK, raw.getStatusCode());
        assertNotNull(raw.getBody());
        assertFalse(raw.getBody().contains("\"newValue\""),
                "删除项不应输出 newValue 字段（Jackson 已配置 non_null）");
    }

    // ------------------------------------------------------------ 辅助方法

    private HttpEntity<String> jsonEntity(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
