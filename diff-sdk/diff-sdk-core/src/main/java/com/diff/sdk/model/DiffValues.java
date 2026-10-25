package com.diff.sdk.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.Map;

/**
 * 差异值的「存在性判定」与「展示文本」转换工具。
 *
 * <p>差异项里的值统一以 {@link JsonNode} 保存，序列化时能保持原始 JSON 结构；
 * 但前端表格需要的是字符串，两者的转换规则集中在本类，避免各调用方各写一遍。
 *
 * <p>展示文本规则与页面 {@code obj2Str} 保持一致：
 * <ul>
 *   <li>缺失值（{@code null} / {@code MissingNode} / JSON {@code null}）→ {@code null}</li>
 *   <li>对象 / 数组 → 紧凑 JSON 文本（等价于 {@code JSON.stringify}）</li>
 *   <li>数字 / 布尔 → 其 JSON 字面量</li>
 *   <li>其他（主要是字符串）→ 字面量本身，<strong>不带引号</strong></li>
 * </ul>
 */
public final class DiffValues {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DiffValues() {
    }

    /**
     * 判断值是否「不存在」。
     *
     * <p>{@code null}、{@link com.fasterxml.jackson.databind.node.MissingNode}、
     * JSON {@code null} 都视为不存在 —— 这是 ADDED / DELETED 判定的基准，
     * 与前端 {@code oldValue === null || undefined || 'null'} 的口径一致。
     *
     * @param value 差异值，可为 null
     * @return 是否不存在
     */
    public static boolean isAbsent(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof JsonNode) {
            JsonNode node = (JsonNode) value;
            return node.isNull() || node.isMissingNode();
        }
        return false;
    }

    /**
     * 把差异值转成展示用字符串，等价于页面里的 {@code obj2Str}。
     *
     * @param value 差异值，可为 null
     * @return 展示文本；缺失值返回 {@code null}
     */
    public static String toText(Object value) {
        if (isAbsent(value)) {
            return null;
        }
        Object target = value;
        if (target instanceof JsonNode) {
            JsonNode node = (JsonNode) target;
            if (node.isContainerNode() || node.isNumber() || node.isBoolean()) {
                return node.toString();
            }
            return node.asText();
        }
        if (target instanceof Map || target instanceof Collection) {
            return writeJson(target);
        }
        return String.valueOf(target);
    }

    /** 序列化为 JSON 字符串；失败时降级为 {@code String.valueOf} */
    static String writeJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }
}
