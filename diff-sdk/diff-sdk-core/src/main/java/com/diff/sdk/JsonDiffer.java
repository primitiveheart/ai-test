package com.diff.sdk;

import com.diff.sdk.model.DiffConfig;
import com.diff.sdk.model.DiffResult;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * JSON 对比器：把两份 JSON 递归对比成一组 {@link com.diff.sdk.model.DiffItem}。
 *
 * <p>路径统一使用点号分隔（数组下标作为普通数字段，如 {@code items.0.name}），
 * 与前端 JsonDiffViewer 内部格式一致。
 *
 * <p>实现必须是<strong>无状态且线程安全</strong>的：同一实例可被多线程并发调用，
 * 对比行为完全由入参 {@code config} 决定（{@link DiffConfig} 本身不可变）。
 *
 * <pre>{@code
 * JsonDiffer differ = JsonDiffers.create();
 * DiffResult result = differ.diffJson("{\"a\":1}", "{\"a\":2}");
 * }</pre>
 *
 * @see JsonDiffers
 */
public interface JsonDiffer {

    /**
     * 对比两份 JSON 字符串。
     *
     * <p>入参为 {@code null} 或空白时视为「不存在」：
     * 两侧都不存在则返回空结果，仅一侧存在则根路径（{@code ""}）记为 ADDED / DELETED。
     *
     * @param oldJson 旧值 JSON，null 或空白视为不存在
     * @param newJson 新值 JSON，null 或空白视为不存在
     * @param config  对比配置，null 时使用 {@link DiffConfig#defaults()}
     * @return 差异结果
     * @throws IllegalArgumentException 入参不是合法 JSON
     */
    DiffResult diffJson(String oldJson, String newJson, DiffConfig config);

    /**
     * 对比两棵已解析的 JSON 树。
     *
     * <p>{@code null} 节点等价于 {@code null} 字面量（视为不存在），
     * 语义与 {@link #diffJson(String, String, DiffConfig)} 一致，只是省去解析步骤。
     *
     * @param oldNode 旧值节点，null 视为不存在
     * @param newNode 新值节点，null 视为不存在
     * @param config  对比配置，null 时使用 {@link DiffConfig#defaults()}
     * @return 差异结果
     */
    DiffResult diff(JsonNode oldNode, JsonNode newNode, DiffConfig config);

    /** 使用默认配置对比两份 JSON 字符串 */
    default DiffResult diffJson(String oldJson, String newJson) {
        return diffJson(oldJson, newJson, DiffConfig.defaults());
    }

    /** 使用默认配置对比两棵 JSON 树 */
    default DiffResult diff(JsonNode oldNode, JsonNode newNode) {
        return diff(oldNode, newNode, DiffConfig.defaults());
    }
}
