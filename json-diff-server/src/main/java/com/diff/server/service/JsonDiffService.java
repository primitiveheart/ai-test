package com.diff.server.service;

import com.diff.server.config.DiffConfigFactory;
import com.diff.server.config.DiffProperties;
import com.diff.server.exception.InvalidRequestException;
import com.diff.server.exception.PayloadTooLargeException;
import com.diff.server.model.DiffRequest;
import com.diff.server.model.DiffResponse;
import com.diff.sdk.JsonDiffers;
import com.diff.sdk.model.DiffConfig;
import com.diff.sdk.model.DiffResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * 对比业务层：校验入参 → 翻译成 {@link DiffConfig} → 调用 diff-sdk → 包装成前端契约。
 *
 * <p>复用容器里的 {@link ObjectMapper}，避免每次请求都新建一个（构造成本不低）。
 */
@Service
public class JsonDiffService {

    private final DiffConfigFactory configFactory;
    private final DiffProperties properties;
    private final ObjectMapper objectMapper;

    public JsonDiffService(DiffConfigFactory configFactory,
                           DiffProperties properties,
                           ObjectMapper objectMapper) {
        this.configFactory = configFactory;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 对比两份 JSON 文本。
     *
     * @param request 对比请求
     * @return 前端约定的响应结构
     * @throws PayloadTooLargeException 单份 JSON 超过 {@code diff.max-json-length}
     * @throws InvalidRequestException  入参不是合法 JSON，或两侧都为空
     */
    public DiffResponse compare(DiffRequest request) {
        checkLength(request.getOldResult(), "oldResult");
        checkLength(request.getNewResult(), "newResult");
        checkAnySidePresent(request);

        DiffConfig config = configFactory.create(request);

        // 自己解析是为了把错误定位到请求字段名（oldResult / newResult），
        // 而不是 SDK 内部的 oldValue / newValue
        JsonNode oldNode = parse(request.getOldResult(), "oldResult");
        JsonNode newNode = parse(request.getNewResult(), "newResult");

        DiffResult result = JsonDiffers.create(config, objectMapper).diff(oldNode, newNode, config);
        return DiffResponse.ok(result);
    }

    /** 空白视为「无数据」；解析失败转成 400 */
    private JsonNode parse(String json, String field) {
        if (json == null || json.trim().isEmpty()) {
            return NullNode.getInstance();
        }
        try {
            return objectMapper.readTree(json);
        } catch (IOException e) {
            throw new InvalidRequestException(field + " 不是合法 JSON: " + e.getMessage(), e);
        }
    }

    private void checkLength(String json, String field) {
        if (json == null) {
            return;
        }
        if (json.length() > properties.getMaxJsonLength()) {
            throw new PayloadTooLargeException(
                    field + " 长度 " + json.length() + " 超过上限 " + properties.getMaxJsonLength());
        }
    }

    /** 两侧都为空没有对比意义，直接拒绝 */
    private void checkAnySidePresent(DiffRequest request) {
        if (!hasText(request.getOldResult()) && !hasText(request.getNewResult())) {
            throw new InvalidRequestException("oldResult 与 newResult 不能同时为空");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
