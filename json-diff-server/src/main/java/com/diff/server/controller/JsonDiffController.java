package com.diff.server.controller;

import com.diff.server.model.DiffRequest;
import com.diff.server.model.DiffResponse;
import com.diff.server.service.JsonDiffService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON 对比接口，供 json-diff-view 前端调用。
 */
@RestController
@RequestMapping("/api/json-diff")
public class JsonDiffController {

    private final JsonDiffService service;

    public JsonDiffController(JsonDiffService service) {
        this.service = service;
    }

    /**
     * 对比两份 JSON。
     *
     * <pre>{@code
     * POST /api/json-diff/compare
     * {"oldResult": "{\"a\":1}", "newResult": "{\"a\":2}"}
     * }</pre>
     *
     * <p>返回 {@code {status:"ok", summary:{...}, diffResult:[{key,oldValue,newValue}]}}。
     */
    @PostMapping(value = "/compare",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public DiffResponse compare(@RequestBody DiffRequest request) {
        return service.compare(request);
    }

    /** 探活接口，用于前端判断后端是否可用 */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        return body;
    }
}
