package com.diff.server.controller;

import com.diff.server.exception.InvalidRequestException;
import com.diff.server.exception.PayloadTooLargeException;
import com.diff.server.model.DiffRequest;
import com.diff.server.model.DiffResponse;
import com.diff.server.model.DiffRow;
import com.diff.server.model.DiffSummary;
import com.diff.server.service.JsonDiffService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 接口层测试：只验证 HTTP 契约与参数校验，对比逻辑由 service 层测试覆盖 */
@WebMvcTest(JsonDiffController.class)
class JsonDiffControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JsonDiffService service;

    @Test
    void compareReturnsDiffRows() throws Exception {
        when(service.compare(any(DiffRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/json-diff/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldResult\":\"{\\\"name\\\":\\\"Tom\\\"}\","
                                + "\"newResult\":\"{\\\"name\\\":\\\"Jerry\\\"}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.diffResult[0].key").value("name"))
                .andExpect(jsonPath("$.diffResult[0].oldValue").value("Tom"))
                .andExpect(jsonPath("$.diffResult[0].newValue").value("Jerry"))
                .andExpect(jsonPath("$.summary.total").value(1));
    }

    @Test
    void compareRejectsUnreadableBody() throws Exception {
        mockMvc.perform(post("/api/json-diff/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void invalidJsonIsReportedAsBadRequest() throws Exception {
        when(service.compare(any(DiffRequest.class)))
                .thenThrow(new InvalidRequestException("oldResult 不是合法 JSON"));

        mockMvc.perform(post("/api/json-diff/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldResult\":\"\",\"newResult\":\"{}\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionMsg").value("oldResult 不是合法 JSON"));
    }

    @Test
    void oversizedPayloadIsReportedAsPayloadTooLarge() throws Exception {
        when(service.compare(any(DiffRequest.class)))
                .thenThrow(new PayloadTooLargeException("oldResult 长度 20 超过上限 10"));

        mockMvc.perform(post("/api/json-diff/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldResult\":\"\",\"newResult\":\"{}\"}"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void healthEndpointReportsUp() throws Exception {
        mockMvc.perform(get("/api/json-diff/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    // ------------------------------------------------------------ 辅助方法

    private static DiffResponse sampleResponse() {
        DiffResponse response = new DiffResponse();
        response.setStatus("ok");
        response.setExceptionMsg("");
        response.setSummary(new DiffSummary(1, 0, 0, 1));
        response.setDiffResult(Arrays.asList(new DiffRow("name", "Tom", "Jerry")));
        return response;
    }
}
